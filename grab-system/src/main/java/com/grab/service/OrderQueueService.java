package com.grab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.grab.entity.GrabOrder;
import com.grab.entity.GrabRecord;
import com.grab.mapper.GrabRecordMapper;
import com.grab.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 异步落库队列（阶段三：最终一致）
 *
 * 抢购链路：Redis 预扣 → 入队（毫秒级返回）→ 后台线程批量落库
 * 把数据库写入从请求链路摘除，用户只等 Redis（~1ms），数据库只承受"批量事务"。
 *
 * 可靠性设计：
 * 1. Redis AOF 持久化：进程崩溃最多丢 1 秒队列数据
 * 2. 启动即消费：队列残留消息由消费者线程自动续传
 * 3. 已抢拦截：bought:{activityId} Set（SADD 原子）在预扣后立即拦重复用户
 * 4. 唯一索引幂等：消费者落库撞唯一索引时，同 orderNo 已存在 = 重复消费（跳过）；
 *    不同 orderNo = 重复用户竞态（补偿库存）
 * 5. 落库失败：逐条重试 3 次，仍失败 → 补偿库存 + error 日志（避免死循环）
 */
@Slf4j
@Service
public class OrderQueueService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private GrabRecordMapper grabRecordMapper;

    @Autowired
    private StockService stockService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /** 消费者线程开关（测试环境关闭：保证 flushNow 是唯一消费入口，断言确定） */
    @org.springframework.beans.factory.annotation.Value("${grab.async-flush.enabled:true}")
    private boolean consumerEnabled;

    /** 待落库订单队列（消息为 JSON：userId/activityId/quantity/orderNo/expireTime） */
    private static final String QUEUE_KEY = "order:queue";

    /** 已抢用户集合前缀：bought:{activityId} */
    private static final String BOUGHT_KEY_PREFIX = "bought:";

    /** 批量落库：攒够 N 条或间隔到期就刷一次 */
    private static final int BATCH_SIZE = 100;
    private static final long FLUSH_INTERVAL_MS = 500;

    /** 单条落库失败重试次数（超过则补偿库存，防止死循环） */
    private static final int MAX_RETRY = 3;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 启动后台消费者线程（应用启动时执行，测试环境同样生效——最终一致断言幂等安全）
     */
    @PostConstruct
    public void startConsumer() {
        if (!consumerEnabled) {
            log.info("订单异步落库消费者未启动（grab.async-flush.enabled=false）");
            return;
        }
        Thread worker = new Thread(this::consumeLoop, "order-flush-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("订单异步落库消费者已启动（批量 {} 条 / 间隔 {}ms）", BATCH_SIZE, FLUSH_INTERVAL_MS);
    }

    /**
     * 抢购用户标记（原子）：返回 true 表示该用户首次抢此活动（可以继续）
     * SADD 原子性保证并发下同一用户只放行一次
     */
    public boolean tryMarkBought(Long activityId, Long userId) {
        Long added = redisTemplate.opsForSet().add(BOUGHT_KEY_PREFIX + activityId, userId);
        return added != null && added == 1L;
    }

    /**
     * 移除抢购标记（取消订单时调用，允许用户重新参与）
     */
    public void removeBought(Long activityId, Long userId) {
        redisTemplate.opsForSet().remove(BOUGHT_KEY_PREFIX + activityId, userId);
    }

    /**
     * 订单入队（预扣成功后调用，毫秒级）
     */
    public void enqueue(GrabOrder order) {
        redisTemplate.opsForList().rightPush(QUEUE_KEY, toJson(order));
    }

    /**
     * 队列中该活动的未落库订单数（对账重建用：Redis 库存丢失时 = 已售订单数）
     */
    public int queueCount(Long activityId) {
        List<Object> all = redisTemplate.opsForList().range(QUEUE_KEY, 0, -1);
        if (all == null) {
            return 0;
        }
        int count = 0;
        for (Object item : all) {
            GrabOrder order = parseOrder(String.valueOf(item));
            if (order != null && activityId.equals(order.getActivityId())) {
                count += order.getQuantity();
            }
        }
        return count;
    }

    /**
     * 手动触发一次消费（测试/核对用：压测后调用可立即把队列清空落库）
     */
    public int flushNow() {
        List<GrabOrder> batch = new ArrayList<>();
        Object json = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        while (json != null && batch.size() < BATCH_SIZE * 10) {
            GrabOrder order = parseOrder(String.valueOf(json));
            if (order != null) {
                batch.add(order);
            }
            json = redisTemplate.opsForList().leftPop(QUEUE_KEY);
        }
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
        return batch.size();
    }

    /**
     * 后台消费循环：攒批（满 BATCH_SIZE 或间隔到期）→ 批量落库
     */
    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                List<GrabOrder> batch = new ArrayList<>();
                long deadline = System.currentTimeMillis() + FLUSH_INTERVAL_MS;
                while (batch.size() < BATCH_SIZE && System.currentTimeMillis() < deadline) {
                    Object json = redisTemplate.opsForList().leftPop(QUEUE_KEY, 100, TimeUnit.MILLISECONDS);
                    if (json != null) {
                        GrabOrder order = parseOrder(String.valueOf(json));
                        if (order != null) {
                            batch.add(order);
                        }
                    }
                }
                if (!batch.isEmpty()) {
                    flushBatch(batch);
                }
            } catch (Exception e) {
                log.error("订单消费线程异常，退避 1s 重试", e);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /**
     * 批量落库（单事务）：整批失败时逐条重试，保证部分成功不阻塞全部
     */
    private void flushBatch(List<GrabOrder> batch) {
        try {
            transactionTemplate.execute(status -> {
                for (GrabOrder order : batch) {
                    insertOrderWithRecord(order);
                }
                return null;
            });
        } catch (DuplicateKeyException e) {
            // 批量里有重复（重复消费/竞态）→ 逐条处理，重复的按幂等规则跳过或补偿
            for (GrabOrder order : batch) {
                insertOneWithIdempotent(order, e);
            }
        } catch (RuntimeException e) {
            // 整批失败（连接异常等）→ 逐条重试
            for (GrabOrder order : batch) {
                insertOneWithRetry(order, e);
            }
        }
    }

    /**
     * 单条落库（幂等处理）：撞唯一索引时区分"重复消费"（跳过）和"重复用户竞态"（补偿库存）
     */
    private void insertOneWithIdempotent(GrabOrder order, RuntimeException cause) {
        try {
            transactionTemplate.execute(status -> {
                insertOrderWithRecord(order);
                return null;
            });
        } catch (DuplicateKeyException e) {
            handleDuplicate(order, e);
        } catch (RuntimeException e) {
            insertOneWithRetry(order, e);
        }
    }

    /**
     * 单条落库（重试）：失败重试 MAX_RETRY 次，仍失败则补偿库存（预扣了就要还）
     */
    private void insertOneWithRetry(GrabOrder order, RuntimeException cause) {
        for (int i = 0; i < MAX_RETRY; i++) {
            try {
                transactionTemplate.execute(status -> {
                    insertOrderWithRecord(order);
                    return null;
                });
                return;
            } catch (DuplicateKeyException e) {
                handleDuplicate(order, e);
                return;
            } catch (RuntimeException e) {
                if (i == MAX_RETRY - 1) {
                    // 重试耗尽：补偿库存 + 记录（丢弃消息避免死循环）
                    stockService.rollback(order.getActivityId(), order.getQuantity());
                    log.error("订单落库重试 {} 次失败，已补偿库存: orderNo={}", MAX_RETRY, order.getOrderNo(), e);
                }
            }
        }
    }

    /**
     * 唯一索引冲突处理：同 orderNo 已存在 = 本消息重复消费（跳过）；
     * 不存在 = 该用户已抢过（理论上已被 bought Set 拦截，兜底补偿库存）
     */
    private void handleDuplicate(GrabOrder order, DuplicateKeyException e) {
        GrabOrder exist = orderMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GrabOrder>()
                        .eq("order_no", order.getOrderNo()));
        if (exist == null) {
            stockService.rollback(order.getActivityId(), order.getQuantity());
            log.warn("重复用户竞态兜底，补偿库存: userId={} activityId={}", order.getUserId(), order.getActivityId());
        }
        // else: 重复消费，跳过即可（订单已存在）
    }

    /**
     * 插入订单 + 抢购记录（由调用方事务包裹；grab_record 唯一索引 user+activity 兜底限购）
     */
    private void insertOrderWithRecord(GrabOrder order) {
        GrabOrder dbOrder = new GrabOrder();
        dbOrder.setOrderNo(order.getOrderNo());
        dbOrder.setUserId(order.getUserId());
        dbOrder.setActivityId(order.getActivityId());
        dbOrder.setQuantity(order.getQuantity());
        dbOrder.setStatus(0); // 待支付
        dbOrder.setExpireTime(order.getExpireTime());
        orderMapper.insert(dbOrder);

        GrabRecord record = new GrabRecord();
        record.setUserId(order.getUserId());
        record.setActivityId(order.getActivityId());
        record.setOrderId(dbOrder.getId());
        grabRecordMapper.insert(record);
    }

    /**
     * 消息序列化：只存必要字段（避免 LocalDateTime 序列化依赖，用字符串时间）
     */
    private String toJson(GrabOrder order) {
        try {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", order.getUserId());
            m.put("activityId", order.getActivityId());
            m.put("quantity", order.getQuantity());
            m.put("orderNo", order.getOrderNo());
            m.put("expireTime", order.getExpireTime() == null ? null : order.getExpireTime().toString());
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("订单消息序列化失败", e);
        }
    }

    /**
     * 消息反序列化（解析失败返回 null，丢弃脏消息）
     */
    private GrabOrder parseOrder(String json) {
        try {
            Map<String, Object> m = objectMapper.readValue(json, Map.class);
            GrabOrder order = new GrabOrder();
            order.setUserId(Long.valueOf(String.valueOf(m.get("userId"))));
            order.setActivityId(Long.valueOf(String.valueOf(m.get("activityId"))));
            order.setQuantity(Integer.valueOf(String.valueOf(m.get("quantity"))));
            order.setOrderNo(String.valueOf(m.get("orderNo")));
            String expire = (String) m.get("expireTime");
            order.setExpireTime(expire == null ? null : LocalDateTime.parse(expire));
            return order;
        } catch (Exception e) {
            log.error("订单消息解析失败，丢弃: {}", json, e);
            return null;
        }
    }
}

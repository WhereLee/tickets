package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.GrabApplication;
import com.grab.entity.Activity;
import com.grab.entity.GrabOrder;
import com.grab.entity.GrabRecord;
import com.grab.mapper.GrabRecordMapper;
import com.grab.mapper.OrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 抢购并发正确性测试（步骤 4：Redis 预扣减 + 数据库真源）
 *
 * 验证：100 个不同用户并发抢库存 50 的活动
 * - 恰好 50 人成功、50 人"库存不足"
 * - 数据库订单 50、记录 50、库存 0（无超卖）
 * - Redis 库存恰好 0
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("抢购并发正确性测试")
public class OrderServiceConcurrentTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private GrabRecordMapper grabRecordMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private OrderQueueService orderQueueService;

    @Test
    @DisplayName("100 线程抢库存 50：恰好 50 成功、50 库存不足、无超卖")
    public void testConcurrentGrab() throws InterruptedException {
        // 准备活动：库存 50，每人限购 1
        Activity activity = new Activity();
        activity.setTitle("并发测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(50);
        activity.setAvailableStock(50);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        final Long targetActivityId = activity.getId();

        // 100 个用户并发抢购（userId 2~101）
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger insufficient = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            long userId = 2 + i;
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.grab(userId, targetActivityId, 1);
                    success.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if ("库存不足".equals(e.getMessage())) {
                        insufficient.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        // 业务断言
        assertEquals(50, success.get(), "恰好 50 人抢到");
        assertEquals(50, insufficient.get(), "恰好 50 人库存不足");

        // 数据库核对：订单 50、记录 50（异步落库：先 flush 队列再核对）
        orderQueueService.flushNow();
        Long activityId = activity.getId();
        int orderCount = orderMapper.selectList(
                new QueryWrapper<GrabOrder>().eq("activity_id", activityId)
        ).size();
        int recordCount = grabRecordMapper.selectList(
                new QueryWrapper<GrabRecord>().eq("activity_id", activityId)
        ).size();
        Activity after = activityService.getById(activityId);

        assertEquals(50, orderCount, "数据库订单必须恰好 50");
        assertEquals(50, recordCount, "抢购记录必须恰好 50");
        assertEquals(50, after.getAvailableStock(), "数据库库存字段不再参与抢购，应保持初始值");

        // Redis 库存核对
        Object redisStock = redisTemplate.opsForValue().get("stock:" + activityId);
        assertEquals(0L, Long.valueOf(redisStock.toString()), "Redis 库存必须为 0");
    }

    @Test
    @DisplayName("对账重建：Redis 库存 key 丢失后，用 total - 已售订单数恢复")
    public void testReconcileAfterKeyLoss() {
        // 活动库存 100
        Activity activity = new Activity();
        activity.setTitle("对账测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(100);
        activity.setAvailableStock(100);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        final Long targetActivityId = activity.getId();

        // 3 个用户各抢 1 个（用户 2/3/4）
        orderService.grab(2L, targetActivityId, 1);
        orderService.grab(3L, targetActivityId, 1);
        orderService.grab(4L, targetActivityId, 1);
        assertEquals(97, stockService.getStock(targetActivityId), "抢 3 个后 Redis 库存应为 97");

        // 模拟 Redis 库存丢失（如 Redis 重启）
        redisTemplate.delete("stock:" + targetActivityId);

        // 再抢 1 个：应触发对账重建（100 - 3 = 97），扣 1 → 96
        GrabOrder order = orderService.grab(5L, targetActivityId, 1);
        assertNotNull(order, "对账重建后应能继续抢购");
        assertEquals(96, stockService.getStock(targetActivityId), "对账重建并扣减后应为 96");
    }

    @Test
    @DisplayName("并发重复抢购：同一用户并发抢两次，恰 1 成功 1 友好拒绝（唯一索引兜底）")
    public void testConcurrentDuplicateGrab() throws InterruptedException {
        // 活动库存 10
        Activity activity = new Activity();
        activity.setTitle("重复抢购测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(10);
        activity.setAvailableStock(10);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        final Long targetActivityId = activity.getId();

        // 同一用户（id=2）并发抢两次
        int threads = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicateMsg = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    orderService.grab(2L, targetActivityId, 1);
                    success.incrementAndGet();
                } catch (IllegalArgumentException e) {
                    if ("您已参与过该活动".equals(e.getMessage())) {
                        duplicateMsg.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        start.countDown();
        done.await();
        pool.shutdown();

        assertEquals(1, success.get(), "必须恰 1 次成功");
        assertEquals(1, duplicateMsg.get(), "另一次必须返回友好消息'您已参与过该活动'（而非系统异常）");
        // 库存只扣 1
        assertEquals(9, stockService.getStock(targetActivityId), "库存只应扣 1（失败的请求必须补偿）");
    }
}

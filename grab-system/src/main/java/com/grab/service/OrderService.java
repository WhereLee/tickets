package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.Activity;
import com.grab.entity.GrabOrder;
import com.grab.entity.GrabRecord;
import com.grab.mapper.GrabRecordMapper;
import com.grab.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 订单 Service
 */
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private GrabRecordMapper grabRecordMapper;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private StockService stockService;

    @Autowired
    private OrderQueueService orderQueueService;

    /**
     * 查询用户订单
     */
    public List<GrabOrder> listByUserId(Long userId) {
        return orderMapper.selectList(
            new QueryWrapper<GrabOrder>()
                .eq("user_id", userId)
                .orderByDesc("create_time")
        );
    }

    /**
     * 根据订单号查询
     */
    public GrabOrder getByOrderNo(String orderNo) {
        return orderMapper.selectOne(new QueryWrapper<GrabOrder>().eq("order_no", orderNo));
    }

    /**
     * 抢购（阶段三：Redis 预扣 + 异步入队，毫秒级返回）
     *
     * 流程（全程不碰数据库连接）：
     * 1-3. 校验活动/时间/数量（活动走 Redis 缓存）
     * 4.   Redis 预扣减（Lua 原子；key 丢失时用 total - 已售订单数（含队列）对账重建）
     * 5.   Redis Set 限购拦截（SADD 原子，重复用户直接拒绝，不碰数据库唯一索引）
     * 6.   订单入队 → 立即返回（最终由后台线程批量落库）
     *
     * 最终一致：用户看到的“抢购成功”是预扣成功；订单落库由 OrderQueueService
     * 后台批量完成（幂等 + 重试 + 失败补偿），崩溃时队列消息由 AOF + 启动续传保障。
     */
    public GrabOrder grab(Long userId, Long activityId, Integer quantity) {
        // 1. 查询活动（走 Redis 缓存：抢购期间活动信息不变；失败者全程不碰 MySQL 连接池）
        Activity activity = activityService.getByIdWithCache(activityId);
        if (activity == null) {
            throw new IllegalArgumentException("活动不存在");
        }

        // 2. 检查活动时间
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(activity.getStartTime())) {
            throw new IllegalArgumentException("活动未开始");
        }
        if (now.isAfter(activity.getEndTime())) {
            throw new IllegalArgumentException("活动已结束");
        }

        // 3. 检查限购数量
        if (quantity > activity.getLimitPerUser()) {
            throw new IllegalArgumentException("超过限购数量");
        }

        // 4. Redis 预扣减库存（事务外：失败者不占用数据库连接池）
        //    key 不存在（-2，如 Redis 重启）时对账重建：总库存 - 已售订单数（含队列中未落库的），再重试一次
        Long deductCode = stockService.tryDeductCode(activityId, quantity);
        if (deductCode != null && deductCode == -2L) {
            int sold = orderMapper.countSold(activityId) + orderQueueService.queueCount(activityId);
            stockService.initIfAbsent(activityId, activity.getTotalStock() - sold);
            deductCode = stockService.tryDeductCode(activityId, quantity);
        }
        if (deductCode == null || deductCode < 0) {
            throw new IllegalArgumentException("库存不足");
        }

        // 5. 限购拦截（Redis Set 原子，不碰数据库）：SADD 返回 0 = 该用户已抢过
        if (!orderQueueService.tryMarkBought(activityId, userId)) {
            stockService.rollback(activityId, quantity); // 重复用户：预扣的库存要还
            throw new IllegalArgumentException("您已参与过该活动");
        }

        // 6. 构造订单草稿 → 入队（毫秒级返回，不等待数据库）
        GrabOrder order = new GrabOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setQuantity(quantity);
        order.setStatus(0); // 待支付
        order.setExpireTime(now.plusMinutes(30)); // 30分钟后过期
        orderQueueService.enqueue(order);
        return order;
    }

    /**
     * 支付订单
     */
    @Transactional
    public void pay(String orderNo) {
        GrabOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new IllegalArgumentException("订单状态异常");
        }

        order.setStatus(1); // 已支付
        order.setPayTime(LocalDateTime.now());
        orderMapper.updateById(order);
    }

    /**
     * 取消订单（异步落库兼容）
     * 订单可能还在队列中未落库：此时查库不存在，返回“处理中”提示用户稍后重试
     */
    @Transactional
    public void cancel(String orderNo) {
        GrabOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单处理中，请稍后重试");
        }
        if (order.getStatus() != 0) {
            throw new IllegalArgumentException("订单状态异常");
        }

        // 更新订单状态
        order.setStatus(2); // 已取消
        orderMapper.updateById(order);

        // 回滚 Redis 预扣库存 + 移除限购标记（允许该用户重新参与）
        stockService.rollback(order.getActivityId(), order.getQuantity());
        orderQueueService.removeBought(order.getActivityId(), order.getUserId());
    }

    /**
     * 生成订单号：毫秒时间戳 + 6位随机数
     * （原秒级+4位随机在并发下单秒内撞号概率高，order_no 唯一索引会拒绝——必须毫秒级+大随机空间）
     * 企业级替代：雪花算法（趋势递增、无碰撞）
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        int random = ThreadLocalRandom.current().nextInt(100000, 999999);
        return timestamp + random;
    }
}

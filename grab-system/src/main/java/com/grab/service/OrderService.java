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
     * 抢购（阶段二步骤 5：Redis 预扣 + 订单落库，不碰数据库库存行）
     *
     * 流程：
     * 1-4. 校验活动/时间/限购（不变）
     * 5.   Redis 预扣减（Lua 原子判断+扣减；key 丢失时用 total - 已售订单数对账重建）
     * 6.   建订单 + 抢购记录（只插自己的行，无行锁竞争）
     * 失败补偿：事务内任何异常 → Redis 库存加回（INCR）
     *
     * 设计变化：数据库 available_stock 字段不再参与抢购（退化为展示值），
     * 活动期间 Redis 库存是真源，订单表是最终记录
     */
    @Transactional
    public GrabOrder grab(Long userId, Long activityId, Integer quantity) {
        // 1. 查询活动
        Activity activity = activityService.getById(activityId);
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

        // 3. 检查是否已抢购过（限购检查）
        GrabRecord existRecord = grabRecordMapper.selectOne(
            new QueryWrapper<GrabRecord>()
                .eq("user_id", userId)
                .eq("activity_id", activityId)
        );
        if (existRecord != null) {
            throw new IllegalArgumentException("您已参与过该活动");
        }

        // 4. 检查限购数量
        if (quantity > activity.getLimitPerUser()) {
            throw new IllegalArgumentException("超过限购数量");
        }

        // 5. Redis 预扣减库存（Lua 原子判断+扣减，拦截大部分请求）
        //    库存 key 丢失时（如 Redis 重启）对账重建：总库存 - 已售订单数
        if (!stockService.hasKey(activityId)) {
            int sold = orderMapper.countSold(activityId);
            stockService.initIfAbsent(activityId, activity.getTotalStock() - sold);
        }
        if (!stockService.tryDeduct(activityId, quantity)) {
            throw new IllegalArgumentException("库存不足");
        }

        try {
            // 6. 创建订单（只插自己的行，无行锁竞争）
            GrabOrder order = new GrabOrder();
            order.setOrderNo(generateOrderNo());
            order.setUserId(userId);
            order.setActivityId(activityId);
            order.setQuantity(quantity);
            order.setStatus(0); // 待支付
            order.setExpireTime(now.plusMinutes(30)); // 30分钟后过期
            orderMapper.insert(order);

            // 7. 记录抢购记录
            GrabRecord record = new GrabRecord();
            record.setUserId(userId);
            record.setActivityId(activityId);
            record.setOrderId(order.getId());
            grabRecordMapper.insert(record);

            return order;
        } catch (DuplicateKeyException e) {
            // 幂等兜底：唯一索引冲突 = 该用户已抢过（并发时 check-then-act 竞态的最后防线）
            stockService.rollback(activityId, quantity);
            throw new IllegalArgumentException("您已参与过该活动");
        } catch (RuntimeException e) {
            // 补偿：事务内失败（订单没建成），把 Redis 预扣的库存加回
            stockService.rollback(activityId, quantity);
            throw e;
        }
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
     * 取消订单
     */
    @Transactional
    public void cancel(String orderNo) {
        GrabOrder order = getByOrderNo(orderNo);
        if (order == null) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new IllegalArgumentException("订单状态异常");
        }

        // 更新订单状态
        order.setStatus(2); // 已取消
        orderMapper.updateById(order);

        // 回滚 Redis 预扣库存（数据库库存字段已不参与抢购，无需回滚）
        stockService.rollback(order.getActivityId(), order.getQuantity());
    }

    /**
     * 生成订单号：年月日时分秒 + 4位随机数
     */
    private String generateOrderNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int random = ThreadLocalRandom.current().nextInt(1000, 9999);
        return timestamp + random;
    }
}

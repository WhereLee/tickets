package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.Activity;
import com.grab.entity.GrabOrder;
import com.grab.entity.GrabRecord;
import com.grab.mapper.GrabRecordMapper;
import com.grab.mapper.OrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
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
     * 抢购（阶段一：基础版本）
     * 
     * 问题：
     * 1. 没有并发控制，可能超卖
     * 2. 没有分布式锁，集群环境下会出问题
     * 3. 性能差，每次都要查数据库
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

        // 5. 检查库存
        if (activity.getAvailableStock() < quantity) {
            throw new IllegalArgumentException("库存不足");
        }

        // 6. 扣减库存
        boolean success = activityService.deductStock(activityId, quantity);
        if (!success) {
            throw new IllegalArgumentException("库存不足");
        }

        // 7. 创建订单
        GrabOrder order = new GrabOrder();
        order.setOrderNo(generateOrderNo());
        order.setUserId(userId);
        order.setActivityId(activityId);
        order.setQuantity(quantity);
        order.setStatus(0); // 待支付
        order.setExpireTime(now.plusMinutes(30)); // 30分钟后过期
        orderMapper.insert(order);

        // 8. 记录抢购记录
        GrabRecord record = new GrabRecord();
        record.setUserId(userId);
        record.setActivityId(activityId);
        record.setOrderId(order.getId());
        grabRecordMapper.insert(record);

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

        // 回滚库存
        activityService.rollbackStock(order.getActivityId(), order.getQuantity());
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

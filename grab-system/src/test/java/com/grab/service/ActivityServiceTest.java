package com.grab.service;

import com.grab.GrabApplication;
import com.grab.entity.Activity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 活动 Service 测试
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("活动模块测试")
public class ActivityServiceTest {

    @Autowired
    private ActivityService activityService;

    @Test
    @Order(1)
    @DisplayName("测试创建活动")
    public void testCreate() {
        Activity activity = new Activity();
        activity.setTitle("测试活动_" + System.currentTimeMillis());
        activity.setDescription("这是一个测试活动");
        activity.setActivityType(1);
        activity.setTotalStock(100);
        activity.setAvailableStock(100);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        
        Activity created = activityService.create(activity);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(0, created.getStatus()); // 未开始
    }

    @Test
    @Order(2)
    @DisplayName("测试查询进行中的活动")
    public void testListActive() {
        List<Activity> activities = activityService.listActive();
        assertNotNull(activities);
        // 至少有一个进行中的活动（init.sql 中插入的测试数据）
    }

    @Test
    @Order(3)
    @DisplayName("测试扣减库存 - 正常情况")
    public void testDeductStock_Success() {
        // 先创建一个活动
        Activity activity = new Activity();
        activity.setTitle("库存测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(10);
        activity.setAvailableStock(10);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        
        // 扣减库存
        boolean success = activityService.deductStock(activity.getId(), 3);
        assertTrue(success);
        
        // 验证库存
        Activity updated = activityService.getById(activity.getId());
        assertEquals(7, updated.getAvailableStock());
    }

    @Test
    @Order(4)
    @DisplayName("测试扣减库存 - 库存不足")
    public void testDeductStock_InsufficientStock() {
        Activity activity = new Activity();
        activity.setTitle("库存不足测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(5);
        activity.setAvailableStock(5);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        
        // 尝试扣减超过库存的数量
        boolean success = activityService.deductStock(activity.getId(), 10);
        assertFalse(success);
    }

    @Test
    @Order(5)
    @DisplayName("测试回滚库存")
    public void testRollbackStock() {
        Activity activity = new Activity();
        activity.setTitle("回滚测试_" + System.currentTimeMillis());
        activity.setActivityType(1);
        activity.setTotalStock(10);
        activity.setAvailableStock(10);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        activity = activityService.create(activity);
        
        // 先扣减
        activityService.deductStock(activity.getId(), 3);
        assertEquals(7, activityService.getById(activity.getId()).getAvailableStock());
        
        // 再回滚
        activityService.rollbackStock(activity.getId(), 3);
        assertEquals(10, activityService.getById(activity.getId()).getAvailableStock());
    }
}

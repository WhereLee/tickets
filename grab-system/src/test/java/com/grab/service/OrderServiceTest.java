package com.grab.service;

import com.grab.GrabApplication;
import com.grab.entity.Activity;
import com.grab.entity.GrabOrder;
import com.grab.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单 Service 测试
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("订单模块测试")
public class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private StockService stockService;

    private User testUser;
    private Activity testActivity;

    @BeforeEach
    public void setUp() {
        // 创建测试用户
        String username = "order_test_" + System.currentTimeMillis();
        testUser = userService.register(username, "123456", "13800138000");
        
        // 创建测试活动
        testActivity = new Activity();
        testActivity.setTitle("订单测试活动_" + System.currentTimeMillis());
        testActivity.setActivityType(1);
        testActivity.setTotalStock(100);
        testActivity.setAvailableStock(100);
        testActivity.setStartTime(LocalDateTime.now().minusHours(1));
        testActivity.setEndTime(LocalDateTime.now().plusHours(1));
        testActivity.setLimitPerUser(1);
        testActivity = activityService.create(testActivity);
    }

    @Test
    @Order(1)
    @DisplayName("测试抢购 - 正常情况")
    public void testGrab_Success() {
        GrabOrder order = orderService.grab(testUser.getId(), testActivity.getId(), 1);
        
        assertNotNull(order);
        assertNotNull(order.getOrderNo());
        assertEquals(testUser.getId(), order.getUserId());
        assertEquals(testActivity.getId(), order.getActivityId());
        assertEquals(1, order.getQuantity());
        assertEquals(0, order.getStatus()); // 待支付
    }

    @Test
    @Order(2)
    @DisplayName("测试抢购 - 活动不存在")
    public void testGrab_ActivityNotFound() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.grab(testUser.getId(), 99999L, 1);
        });
        assertEquals("活动不存在", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("测试抢购 - 重复抢购")
    public void testGrab_DuplicateGrab() {
        // 第一次抢购
        orderService.grab(testUser.getId(), testActivity.getId(), 1);
        
        // 第二次抢购应该失败
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.grab(testUser.getId(), testActivity.getId(), 1);
        });
        assertEquals("您已参与过该活动", exception.getMessage());
    }

    @Test
    @Order(4)
    @DisplayName("测试抢购 - 超过限购数量")
    public void testGrab_ExceedLimit() {
        // 活动限购1个，尝试购买2个
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            orderService.grab(testUser.getId(), testActivity.getId(), 2);
        });
        assertEquals("超过限购数量", exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("测试支付订单")
    public void testPay() {
        // 先抢购
        GrabOrder order = orderService.grab(testUser.getId(), testActivity.getId(), 1);
        
        // 支付
        orderService.pay(order.getOrderNo());
        
        // 验证状态
        GrabOrder paidOrder = orderService.getByOrderNo(order.getOrderNo());
        assertEquals(1, paidOrder.getStatus()); // 已支付
        assertNotNull(paidOrder.getPayTime());
    }

    @Test
    @Order(6)
    @DisplayName("测试取消订单 - Redis 库存回滚")
    public void testCancel_RollbackStock() {
        Long activityId = testActivity.getId();
        // 抢购前 Redis 库存（key 不存在时用总库存兜底：对账 = total - 0）
        Integer stockBefore = stockService.getStock(activityId);
        if (stockBefore == null) {
            stockBefore = testActivity.getTotalStock();
        }

        // 抢购
        GrabOrder order = orderService.grab(testUser.getId(), activityId, 1);

        // 抢购后：Redis 库存减 1
        assertEquals(stockBefore - 1, stockService.getStock(activityId), "抢购后 Redis 库存应减 1");

        // 取消
        orderService.cancel(order.getOrderNo());

        // 取消后：Redis 库存回滚
        assertEquals(stockBefore, stockService.getStock(activityId), "取消后 Redis 库存应回滚");

        // 验证订单状态
        GrabOrder cancelledOrder = orderService.getByOrderNo(order.getOrderNo());
        assertEquals(2, cancelledOrder.getStatus()); // 已取消
    }

    @Test
    @Order(7)
    @DisplayName("测试查询用户订单")
    public void testListByUserId() {
        // 抢购几个订单
        orderService.grab(testUser.getId(), testActivity.getId(), 1);
        
        // 查询
        List<GrabOrder> orders = orderService.listByUserId(testUser.getId());
        assertNotNull(orders);
        assertFalse(orders.isEmpty());
    }
}

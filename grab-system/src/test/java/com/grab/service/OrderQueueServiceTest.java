package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.GrabApplication;
import com.grab.entity.Activity;
import com.grab.entity.GrabOrder;
import com.grab.entity.GrabRecord;
import com.grab.entity.User;
import com.grab.mapper.GrabRecordMapper;
import com.grab.mapper.OrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单异步落库队列测试（阶段三）
 *
 * 验证：
 * - 抢购入队后数据库不可见，flush 后落库（订单 + 记录）
 * - 重复消费幂等（flush 两次不产生重复订单）
 * - 已抢用户 Redis Set 拦截（并发重复抢购只能成功一次）
 * - queueCount 对账计数（队列中未落库订单数）
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("订单异步落库队列测试")
public class OrderQueueServiceTest {

    @Autowired
    private OrderQueueService orderQueueService;

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private ActivityService activityService;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private StockService stockService;

    @Autowired
    private GrabRecordMapper grabRecordMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private User testUser;
    private Activity testActivity;

    @BeforeEach
    public void setUp() {
        String username = "queue_test_" + System.currentTimeMillis();
        testUser = userService.register(username, "123456", "13800138000");

        testActivity = new Activity();
        testActivity.setTitle("队列测试活动_" + System.currentTimeMillis());
        testActivity.setActivityType(1);
        testActivity.setTotalStock(100);
        testActivity.setAvailableStock(100);
        testActivity.setStartTime(LocalDateTime.now().minusHours(1));
        testActivity.setEndTime(LocalDateTime.now().plusHours(1));
        testActivity.setLimitPerUser(1);
        testActivity = activityService.create(testActivity);
    }

    @Test
    @DisplayName("抢购入队 → flush 后落库（订单 + 记录）")
    public void testEnqueueThenFlush() {
        // 抢购：预扣成功 + 入队（数据库此时无订单）
        GrabOrder order = orderService.grab(testUser.getId(), testActivity.getId(), 1);
        assertNull(orderMapper.selectOne(new QueryWrapper<GrabOrder>().eq("order_no", order.getOrderNo())),
                "flush 前订单不应在数据库");

        // flush 落库
        int flushed = orderQueueService.flushNow();
        assertTrue(flushed >= 1, "应消费至少 1 条消息");

        // 订单 + 记录都在库
        assertNotNull(orderMapper.selectOne(new QueryWrapper<GrabOrder>().eq("order_no", order.getOrderNo())),
                "flush 后订单应落库");
        GrabRecord record = grabRecordMapper.selectOne(
                new QueryWrapper<GrabRecord>()
                        .eq("user_id", testUser.getId())
                        .eq("activity_id", testActivity.getId()));
        assertNotNull(record, "抢购记录应落库");
    }

    @Test
    @DisplayName("重复消费幂等：flush 两次不产生重复订单")
    public void testFlushIdempotent() {
        GrabOrder order = orderService.grab(testUser.getId(), testActivity.getId(), 1);
        orderQueueService.flushNow();
        orderQueueService.flushNow(); // 队列已空，第二次无消息可消费

        long orderCount = orderMapper.selectList(
                new QueryWrapper<GrabOrder>().eq("order_no", order.getOrderNo())).size();
        assertEquals(1, orderCount, "同一订单只能落库一次");
    }

    @Test
    @DisplayName("已抢用户拦截：同用户第二次抢购被拒绝")
    public void testDuplicateUserRejected() {
        orderService.grab(testUser.getId(), testActivity.getId(), 1);

        // 第二次抢购：Redis Set 拦截（SADD 返回 0），库存补偿
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> orderService.grab(testUser.getId(), testActivity.getId(), 1));
        assertEquals("您已参与过该活动", e.getMessage());

        // 库存只扣 1（被拦的请求已补偿）：总库存 100 → Redis 库存 99
        assertEquals(99, stockService.getStock(testActivity.getId()), "库存应只扣 1（重复请求已补偿）");
    }

    @Test
    @DisplayName("queueCount 统计队列中未落库订单数")
    public void testQueueCount() {
        // 3 个用户抢购（不同用户，不触发限购拦截）
        User u2 = userService.register("queue_test2_" + System.currentTimeMillis(), "123456", "13800138001");
        User u3 = userService.register("queue_test3_" + System.currentTimeMillis(), "123456", "13800138002");
        orderService.grab(testUser.getId(), testActivity.getId(), 1);
        orderService.grab(u2.getId(), testActivity.getId(), 1);
        orderService.grab(u3.getId(), testActivity.getId(), 1);

        // 队列中应有 3 条未落库（若消费者已消费部分，countSold 会补上——总和仍是 3）
        int queue = orderQueueService.queueCount(testActivity.getId());
        int sold = orderMapper.countSold(testActivity.getId());
        assertEquals(3, queue + sold, "队列未落库 + 已落库 = 3");
    }
}

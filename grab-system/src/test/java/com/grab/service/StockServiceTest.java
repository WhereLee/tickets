package com.grab.service;

import com.grab.GrabApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 库存服务测试（步骤 4：预扣减）
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("Redis 库存预扣减测试")
public class StockServiceTest {

    @Autowired
    private StockService stockService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 测试活动 id（独立，不碰业务数据） */
    private static final Long ACTIVITY_ID = 100001L;

    private void cleanStock() {
        redisTemplate.delete("stock:" + ACTIVITY_ID);
    }

    private Long getStock() {
        Object v = redisTemplate.opsForValue().get("stock:" + ACTIVITY_ID);
        return v == null ? null : Long.valueOf(v.toString());
    }

    @Test
    @DisplayName("初始化库存：首次写入，重复初始化不覆盖（幂等）")
    public void testInitIfAbsent() {
        cleanStock();

        stockService.initIfAbsent(ACTIVITY_ID, 100);
        assertEquals(100L, getStock());

        // 再次初始化不同值，应不生效（key 已存在）
        stockService.initIfAbsent(ACTIVITY_ID, 999);
        assertEquals(100L, getStock(), "幂等：已存在的库存不能被覆盖");
    }

    @Test
    @DisplayName("扣减成功：库存足够时原子扣减")
    public void testDeductSuccess() {
        cleanStock();
        stockService.initIfAbsent(ACTIVITY_ID, 10);

        assertTrue(stockService.tryDeduct(ACTIVITY_ID, 3));
        assertEquals(7L, getStock());
    }

    @Test
    @DisplayName("扣减失败：库存不足时不扣减（Lua 判断+扣减原子）")
    public void testDeductInsufficient() {
        cleanStock();
        stockService.initIfAbsent(ACTIVITY_ID, 2);

        assertFalse(stockService.tryDeduct(ACTIVITY_ID, 5), "库存不足应拒绝");
        assertEquals(2L, getStock(), "拒绝时库存必须保持不变");
    }

    @Test
    @DisplayName("回滚库存：INCR 加回")
    public void testRollback() {
        cleanStock();
        stockService.initIfAbsent(ACTIVITY_ID, 10);
        stockService.tryDeduct(ACTIVITY_ID, 3);

        stockService.rollback(ACTIVITY_ID, 3);
        assertEquals(10L, getStock(), "回滚后库存应恢复");
    }

    @Test
    @DisplayName("并发正确性：100 线程抢 50 库存，恰好 50 成功、50 失败、无超卖")
    public void testConcurrentDeduct() throws InterruptedException {
        cleanStock();
        stockService.initIfAbsent(ACTIVITY_ID, 50);

        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger success = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    start.await(); // 所有线程就绪后同时开抢
                    if (stockService.tryDeduct(ACTIVITY_ID, 1)) {
                        success.incrementAndGet();
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

        assertEquals(50, success.get(), "必须恰好 50 个成功（库存 50）");
        assertEquals(0L, getStock(), "最终库存必须为 0（无超卖）");
    }
}

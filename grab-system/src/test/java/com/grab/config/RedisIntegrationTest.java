package com.grab.config;

import com.grab.GrabApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 连接与读写测试（步骤 2 验证）
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("Redis 集成测试")
public class RedisIntegrationTest {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Test
    @DisplayName("Redis 连接 + 写入 + 读取")
    public void testSetAndGet() {
        String key = "test:key:" + System.currentTimeMillis();

        // 写入
        redisTemplate.opsForValue().set(key, "hello-redis");

        // 读取
        Object value = redisTemplate.opsForValue().get(key);
        assertEquals("hello-redis", value);

        // 清理
        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis 原子自减（DECR 对应操作）")
    public void testDecrement() {
        String key = "test:stock:" + System.currentTimeMillis();

        // 初始值 100
        redisTemplate.opsForValue().set(key, 100);

        // 原子减 1
        Long after = redisTemplate.opsForValue().decrement(key);
        assertEquals(99L, after);

        // 再次减
        after = redisTemplate.opsForValue().decrement(key);
        assertEquals(98L, after);

        redisTemplate.delete(key);
    }

    @Test
    @DisplayName("Redis 设置过期时间")
    public void testExpire() {
        String key = "test:expire:" + System.currentTimeMillis();
        redisTemplate.opsForValue().set(key, "temp");

        // 设置 1 秒过期
        Boolean result = redisTemplate.expire(key, java.time.Duration.ofSeconds(1));
        assertTrue(result);

        // 立即能读到
        assertNotNull(redisTemplate.opsForValue().get(key));

        // 等 2 秒后应该消失
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        assertNull(redisTemplate.opsForValue().get(key));
    }
}

package com.grab.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Redis 库存服务（步骤 4：预扣减）
 *
 * 设计：Redis 是"预扣"，数据库是"真源"
 * - 抢购前先 Redis 预扣（拦截大部分请求，不碰数据库行锁）
 * - 抢购成功后数据库同步扣减（真源）
 * - 事务失败/取消订单时 Redis 补偿加回
 */
@Service
public class StockService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /** 库存 key 前缀：stock:1 */
    private static final String STOCK_KEY_PREFIX = "stock:";

    /**
     * Lua 脚本：判断库存 >= 数量才扣减（原子操作）
     * 返回：新库存值（>=0 表示成功）；-1 库存不足；-2 key 不存在
     */
    private static final String DEDUCT_LUA =
            "local stock = redis.call('GET', KEYS[1]) " +
            "if not stock then return -2 end " +
            "stock = tonumber(stock) " +
            "if stock >= tonumber(ARGV[1]) then " +
            "  return redis.call('DECRBY', KEYS[1], ARGV[1]) " +
            "end " +
            "return -1";

    private static final DefaultRedisScript<Long> DEDUCT_SCRIPT =
            new DefaultRedisScript<>(DEDUCT_LUA, Long.class);

    /**
     * 初始化库存（只在 key 不存在时写入，幂等）
     */
    public void initIfAbsent(Long activityId, Integer stock) {
        redisTemplate.opsForValue().setIfAbsent(STOCK_KEY_PREFIX + activityId, stock);
    }

    /**
     * 库存 key 是否存在（用于决定是否需要对账重建）
     */
    public boolean hasKey(Long activityId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(STOCK_KEY_PREFIX + activityId));
    }

    /**
     * 读取当前库存（key 不存在返回 null）
     */
    public Integer getStock(Long activityId) {
        Object value = redisTemplate.opsForValue().get(STOCK_KEY_PREFIX + activityId);
        return value == null ? null : Integer.valueOf(value.toString());
    }

    /**
     * 预扣减库存（原子：判断 + 扣减一步完成）
     * @return true 扣减成功；false 库存不足
     */
    public boolean tryDeduct(Long activityId, Integer quantity) {
        Long result = redisTemplate.execute(
                DEDUCT_SCRIPT,
                Collections.singletonList(STOCK_KEY_PREFIX + activityId),
                quantity
        );
        return result != null && result >= 0;
    }

    /**
     * 回滚库存（INCR 加回）
     * 注意：key 不存在时不创建（避免虚低值），由下次 initIfAbsent 用数据库最新值重建
     */
    public void rollback(Long activityId, Integer quantity) {
        String key = STOCK_KEY_PREFIX + activityId;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().increment(key, quantity);
        }
    }
}

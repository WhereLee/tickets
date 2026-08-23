package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.Activity;
import com.grab.mapper.ActivityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 活动 Service
 */
@Service
public class ActivityService {

    @Autowired
    private ActivityMapper activityMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StockService stockService;

    /** 活动缓存 key 前缀 */
    private static final String CACHE_KEY_PREFIX = "activity:";
    /** 活动信息缓存时间：10 分钟 */
    private static final long CACHE_TTL = 10 * 60;
    /** 空值缓存时间：60 秒（防穿透） */
    private static final long NULL_CACHE_TTL = 60;
    /** 空值占位标记 */
    private static final String NULL_MARK = "NULL";

    /**
     * 查询所有活动
     */
    public List<Activity> listAll() {
        return activityMapper.selectList(null);
    }

    /**
     * 查询进行中的活动
     */
    public List<Activity> listActive() {
        LocalDateTime now = LocalDateTime.now();
        return activityMapper.selectList(
            new QueryWrapper<Activity>()
                .eq("status", 1)
                .le("start_time", now)
                .ge("end_time", now)
        );
    }

    /**
     * 根据ID查询（实时查数据库，抢购流程用）
     */
    public Activity getById(Long id) {
        return activityMapper.selectById(id);
    }

    /**
     * 根据ID查询（带缓存，展示接口用）
     * Cache Aside 模式：先查缓存，未命中查库并回填；空值也缓存 60 秒防穿透
     */
    public Activity getByIdWithCache(Long id) {
        String key = CACHE_KEY_PREFIX + id;

        // 1. 先查缓存
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_MARK.equals(cached)) {
                return null; // 缓存了空值：活动确实不存在
            }
            return (Activity) cached;
        }

        // 2. 缓存未命中，查数据库（真源）
        Activity activity = activityMapper.selectById(id);

        // 3. 回填缓存
        if (activity == null) {
            redisTemplate.opsForValue().set(key, NULL_MARK, NULL_CACHE_TTL, TimeUnit.SECONDS);
        } else {
            redisTemplate.opsForValue().set(key, activity, CACHE_TTL, TimeUnit.SECONDS);
        }
        return activity;
    }

    /**
     * 查询活动详情（展示用）：缓存活动信息 + Redis 实时库存覆盖
     * 库存 key 不存在时（未开抢）用数据库字段兜底
     */
    public Activity getByIdWithStock(Long id) {
        Activity activity = getByIdWithCache(id);
        if (activity == null) {
            return null;
        }
        Integer stock = stockService.getStock(id);
        if (stock != null) {
            activity.setAvailableStock(stock);
        }
        return activity;
    }

    /**
     * 创建活动（Cache Aside 写模式：更新数据库后删缓存）
     */
    public Activity create(Activity activity) {
        activity.setStatus(0); // 默认未开始
        activityMapper.insert(activity);
        // 删除缓存，让下次读取重新加载（防止旧缓存残留）
        redisTemplate.delete(CACHE_KEY_PREFIX + activity.getId());
        return activity;
    }

    /**
     * 扣减库存
     */
    public boolean deductStock(Long activityId, Integer quantity) {
        int rows = activityMapper.deductStock(activityId, quantity);
        return rows > 0;
    }

    /**
     * 回滚库存
     */
    public void rollbackStock(Long activityId, Integer quantity) {
        Activity activity = activityMapper.selectById(activityId);
        if (activity != null) {
            activity.setAvailableStock(activity.getAvailableStock() + quantity);
            activityMapper.updateById(activity);
        }
    }
}

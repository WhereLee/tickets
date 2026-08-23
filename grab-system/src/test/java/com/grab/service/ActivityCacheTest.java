package com.grab.service;

import com.grab.GrabApplication;
import com.grab.entity.Activity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 活动缓存测试（步骤 3：Cache Aside 模式）
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("活动缓存测试")
public class ActivityCacheTest {

    @Autowired
    private ActivityService activityService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private Activity createTestActivity() {
        Activity activity = new Activity();
        activity.setTitle("缓存测试_" + System.currentTimeMillis());
        activity.setDescription("缓存测试活动");
        activity.setActivityType(1);
        activity.setTotalStock(100);
        activity.setAvailableStock(100);
        activity.setStartTime(LocalDateTime.now().minusHours(1));
        activity.setEndTime(LocalDateTime.now().plusHours(1));
        activity.setLimitPerUser(1);
        return activityService.create(activity);
    }

    @Test
    @DisplayName("首次查询回填缓存，二次查询命中缓存且类型还原为 Activity")
    public void testCacheHitAndTypeRestore() {
        Activity created = createTestActivity();
        String key = "activity:" + created.getId();

        // create 后缓存应被删除（Cache Aside 写模式）
        assertNull(redisTemplate.opsForValue().get(key), "创建活动后缓存应被删除");

        // 第一次查询：缓存未命中 → 查库 → 回填
        Activity first = activityService.getByIdWithCache(created.getId());
        assertNotNull(first);
        assertNotNull(redisTemplate.opsForValue().get(key), "首次查询后应回填缓存");

        // 第二次查询：命中缓存，类型必须是 Activity（不是 LinkedHashMap）
        Activity second = activityService.getByIdWithCache(created.getId());
        assertNotNull(second);
        assertTrue(second instanceof Activity, "缓存读出的类型必须是 Activity");
        assertEquals(created.getId(), second.getId());
        assertEquals(created.getTitle(), second.getTitle());
    }

    @Test
    @DisplayName("查询不存在的活动：返回 null 且缓存空值标记（防穿透）")
    public void testNullCache() {
        Long id = 999999L;
        String key = "activity:" + id;
        redisTemplate.delete(key);

        Activity result = activityService.getByIdWithCache(id);
        assertNull(result);

        // 空值已缓存
        assertEquals("NULL", redisTemplate.opsForValue().get(key), "空值应缓存 60 秒防穿透");

        // 第二次查询：直接命中空值缓存，仍然返回 null
        assertNull(activityService.getByIdWithCache(id));
    }

    @Test
    @DisplayName("实时查询与缓存查询互不影响（抢购走实时，展示走缓存）")
    public void testRealTimeVsCache() {
        Activity created = createTestActivity();

        // 先走缓存查询（回填缓存）
        Activity cached = activityService.getByIdWithCache(created.getId());
        assertNotNull(cached);

        // 实时查询（抢购流程用）不受缓存影响
        Activity realtime = activityService.getById(created.getId());
        assertNotNull(realtime);
        assertEquals(created.getId(), realtime.getId());
    }
}

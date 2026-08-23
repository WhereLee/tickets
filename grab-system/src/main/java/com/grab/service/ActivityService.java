package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.Activity;
import com.grab.mapper.ActivityMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动 Service
 */
@Service
public class ActivityService {

    @Autowired
    private ActivityMapper activityMapper;

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
     * 根据ID查询
     */
    public Activity getById(Long id) {
        return activityMapper.selectById(id);
    }

    /**
     * 创建活动
     */
    public Activity create(Activity activity) {
        activity.setStatus(0); // 默认未开始
        activityMapper.insert(activity);
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

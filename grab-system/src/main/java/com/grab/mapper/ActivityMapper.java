package com.grab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grab.entity.Activity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 活动 Mapper
 */
public interface ActivityMapper extends BaseMapper<Activity> {

    /**
     * 扣减库存（阶段一：基础版本，后续会优化）
     */
    @Update("UPDATE activity SET available_stock = available_stock - #{quantity} WHERE id = #{activityId} AND available_stock >= #{quantity}")
    int deductStock(@Param("activityId") Long activityId, @Param("quantity") Integer quantity);
}

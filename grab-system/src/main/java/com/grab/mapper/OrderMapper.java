package com.grab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grab.entity.GrabOrder;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 订单 Mapper
 */
public interface OrderMapper extends BaseMapper<GrabOrder> {

    /**
     * 统计活动已售数量（对账用）：待支付(0) + 已支付(1) 订单的 quantity 合计
     */
    @Select("SELECT COALESCE(SUM(quantity), 0) FROM grab_order WHERE activity_id = #{activityId} AND status IN (0, 1)")
    int countSold(@Param("activityId") Long activityId);
}

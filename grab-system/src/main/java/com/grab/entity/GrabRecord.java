package com.grab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抢购记录实体（防止重复抢购）
 */
@Data
@TableName("grab_record")
public class GrabRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 活动ID */
    private Long activityId;

    /** 关联订单ID */
    private Long orderId;

    private LocalDateTime createTime;
}

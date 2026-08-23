package com.grab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抢购订单实体
 */
@Data
@TableName("grab_order")
public class GrabOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 订单号 */
    private String orderNo;

    /** 用户ID */
    private Long userId;

    /** 活动ID */
    private Long activityId;

    /** 购买数量 */
    private Integer quantity;

    /** 状态：0-待支付 1-已支付 2-已取消 3-已过期 */
    private Integer status;

    /** 支付时间 */
    private LocalDateTime payTime;

    /** 过期时间 */
    private LocalDateTime expireTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

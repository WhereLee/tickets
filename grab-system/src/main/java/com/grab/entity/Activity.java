package com.grab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动实体
 */
@Data
@TableName("activity")
public class Activity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 活动标题 */
    private String title;

    /** 活动描述 */
    private String description;

    /** 活动类型：1-优惠券 2-秒杀商品 3-限量名额 */
    private Integer activityType;

    /** 总库存 */
    private Integer totalStock;

    /** 可用库存 */
    private Integer availableStock;

    /** 开始时间 */
    private LocalDateTime startTime;

    /** 结束时间 */
    private LocalDateTime endTime;

    /** 每人限购 */
    private Integer limitPerUser;

    /** 状态：0-未开始 1-进行中 2-已结束 3-已下架 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

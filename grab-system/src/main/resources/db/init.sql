-- 创建数据库
CREATE DATABASE IF NOT EXISTS grab_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;

USE grab_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '用户ID',
    `username` VARCHAR(50) UNIQUE NOT NULL COMMENT '用户名',
    `password` VARCHAR(100) NOT NULL COMMENT '密码',
    `phone` VARCHAR(20) COMMENT '手机号',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 活动表
CREATE TABLE IF NOT EXISTS `activity` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '活动ID',
    `title` VARCHAR(200) NOT NULL COMMENT '活动标题',
    `description` TEXT COMMENT '活动描述',
    `activity_type` TINYINT NOT NULL COMMENT '活动类型：1-优惠券 2-秒杀商品 3-限量名额',
    `total_stock` INT NOT NULL COMMENT '总库存',
    `available_stock` INT NOT NULL COMMENT '可用库存',
    `start_time` DATETIME NOT NULL COMMENT '开始时间',
    `end_time` DATETIME NOT NULL COMMENT '结束时间',
    `limit_per_user` INT DEFAULT 1 COMMENT '每人限购',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-未开始 1-进行中 2-已结束 3-已下架',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

-- 抢购订单表
CREATE TABLE IF NOT EXISTS `grab_order` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(50) UNIQUE NOT NULL COMMENT '订单号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `quantity` INT NOT NULL COMMENT '购买数量',
    `status` TINYINT DEFAULT 0 COMMENT '状态：0-待支付 1-已支付 2-已取消 3-已过期',
    `pay_time` DATETIME COMMENT '支付时间',
    `expire_time` DATETIME NOT NULL COMMENT '过期时间',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_activity_id` (`activity_id`),
    INDEX `idx_expire_time` (`expire_time`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抢购订单表';

-- 抢购记录表（防止重复抢购）
CREATE TABLE IF NOT EXISTS `grab_record` (
    `id` BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '记录ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `activity_id` BIGINT NOT NULL COMMENT '活动ID',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_user_activity` (`user_id`, `activity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='抢购记录表';

-- 插入测试数据（时间使用相对当前时间的写法，保证活动处于进行中）
INSERT INTO `user` (`username`, `password`, `phone`) VALUES
('testuser', '123456', '13800138000');

INSERT INTO `activity` (`title`, `description`, `activity_type`, `total_stock`, `available_stock`, `start_time`, `end_time`, `limit_per_user`, `status`) VALUES
('coupon_sale', 'discount coupon', 1, 100, 100, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), 1, 1),
('flash_sale', 'bluetooth earphone', 2, 50, 50, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), 1, 1);

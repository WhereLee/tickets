package com.grab.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体
 */
@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 密码：序列化时忽略（不返回给前端，即使是密文也不该泄露） */
    @JsonIgnore
    private String password;

    private String phone;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

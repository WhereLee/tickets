package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.User;
import com.grab.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 用户 Service
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /**
     * 根据用户名查询
     */
    public User getByUsername(String username) {
        return userMapper.selectOne(new QueryWrapper<User>().eq("username", username));
    }

    /**
     * 注册
     */
    public User register(String username, String password, String phone) {
        // 检查用户名是否已存在
        User existUser = getByUsername(username);
        if (existUser != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(password); // 阶段一：明文存储，后续加密
        user.setPhone(phone);
        userMapper.insert(user);
        return user;
    }

    /**
     * 登录
     */
    public User login(String username, String password) {
        User user = getByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!user.getPassword().equals(password)) {
            throw new IllegalArgumentException("密码错误");
        }
        return user;
    }
}

package com.grab.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.grab.entity.User;
import com.grab.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户 Service
 */
@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    /** BCrypt 密码编码器（不可逆哈希，同一密码每次加密结果不同） */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
        user.setPassword(passwordEncoder.encode(password)); // BCrypt 加密存储
        user.setPhone(phone);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 并发兜底：两个请求同时注册同名用户，唯一索引冲突
            throw new IllegalArgumentException("用户名已存在");
        }
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
        // BCrypt 比对（内部会解析密文中的盐，无需手动处理）
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("密码错误");
        }
        return user;
    }
}

package com.grab.controller;

import com.grab.common.Result;
import com.grab.entity.User;
import com.grab.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 用户 Controller
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 注册
     */
    @PostMapping("/register")
    public Result<User> register(@RequestParam String username,
                                  @RequestParam String password,
                                  @RequestParam(required = false) String phone) {
        User user = userService.register(username, password, phone);
        user.setPassword(null); // 不返回密码
        return Result.ok("注册成功", user);
    }

    /**
     * 登录
     */
    @PostMapping("/login")
    public Result<User> login(@RequestParam String username,
                               @RequestParam String password) {
        User user = userService.login(username, password);
        user.setPassword(null); // 不返回密码
        return Result.ok("登录成功", user);
    }
}

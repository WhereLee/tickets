package com.grab.service;

import com.grab.GrabApplication;
import com.grab.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户 Service 测试
 */
@SpringBootTest(classes = GrabApplication.class)
@DisplayName("用户模块测试")
public class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    @Order(1)
    @DisplayName("测试用户注册 - 正常情况")
    public void testRegister_Success() {
        String username = "testuser_" + System.currentTimeMillis();
        User user = userService.register(username, "123456", "13800138000");
        
        assertNotNull(user);
        assertNotNull(user.getId());
        assertEquals(username, user.getUsername());
        // 密码必须加密存储（BCrypt 密文以 $2a$ 开头，且不是明文）
        assertNotEquals("123456", user.getPassword(), "密码不能明文存储");
        assertTrue(user.getPassword().startsWith("$2a$"), "BCrypt 密文以 $2a$ 开头");
    }

    @Test
    @Order(2)
    @DisplayName("测试用户注册 - 用户名已存在")
    public void testRegister_DuplicateUsername() {
        String username = "duplicate_" + System.currentTimeMillis();
        userService.register(username, "123456", "13800138000");
        
        // 再次注册相同用户名应该抛出异常
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.register(username, "123456", "13800138000");
        });
        assertEquals("用户名已存在", exception.getMessage());
    }

    @Test
    @Order(3)
    @DisplayName("测试用户登录 - 正常情况")
    public void testLogin_Success() {
        String username = "login_" + System.currentTimeMillis();
        userService.register(username, "123456", "13800138000");
        
        User user = userService.login(username, "123456");
        assertNotNull(user);
        assertEquals(username, user.getUsername());
    }

    @Test
    @Order(4)
    @DisplayName("测试用户登录 - 用户不存在")
    public void testLogin_UserNotFound() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.login("nonexistent_user", "123456");
        });
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    @Order(5)
    @DisplayName("测试用户登录 - 密码错误")
    public void testLogin_WrongPassword() {
        String username = "pwd_" + System.currentTimeMillis();
        userService.register(username, "123456", "13800138000");
        
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            userService.login(username, "wrong_password");
        });
        assertEquals("密码错误", exception.getMessage());
    }
}

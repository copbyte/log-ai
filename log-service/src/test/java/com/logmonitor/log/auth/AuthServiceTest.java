package com.logmonitor.log.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 登录服务测试：成功签发、错误密码、未知用户、空输入。
 */
class AuthServiceTest {

    private AuthProperties properties;
    private AuthService service;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        AuthProperties.AuthUser admin = new AuthProperties.AuthUser();
        admin.setUsername("admin");
        admin.setPassword("{noop}123456");
        properties.setUsers(List.of(admin));
        service = new AuthService(properties, new JwtTokenService(properties), new PasswordMatcher());
    }

    @Test
    void loginSuccessReturnsToken() {
        LoginResponse resp = service.login(new LoginRequest("admin", "123456"));
        assertNotNull(resp.token());
        assertEquals("admin", resp.username());
        assertEquals(7200, resp.expiresInSeconds());
    }

    @Test
    void loginWrongPasswordThrows() {
        assertThrows(AuthException.class, () -> service.login(new LoginRequest("admin", "wrong")));
    }

    @Test
    void loginUnknownUserThrows() {
        assertThrows(AuthException.class, () -> service.login(new LoginRequest("nobody", "123456")));
    }

    @Test
    void loginBlankInputThrows() {
        assertThrows(AuthException.class, () -> service.login(new LoginRequest("", "")));
    }
}

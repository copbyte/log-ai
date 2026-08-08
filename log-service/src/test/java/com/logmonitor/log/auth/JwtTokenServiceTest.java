package com.logmonitor.log.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * JWT 签发/校验测试：正常解析、过期、篡改、非法输入。
 */
class JwtTokenServiceTest {

    private AuthProperties properties;

    @BeforeEach
    void setUp() {
        properties = new AuthProperties();
        properties.setJwtSecret("test-secret-key-with-at-least-32-bytes!!");
        properties.setTokenExpireSeconds(60);
    }

    @Test
    void issueThenParseReturnsUsername() {
        JwtTokenService service = new JwtTokenService(properties);
        String token = service.issue("admin");
        assertNotNull(token);
        assertEquals("admin", service.parseUsername(token));
    }

    @Test
    void expiredTokenReturnsNull() {
        properties.setTokenExpireSeconds(0);
        JwtTokenService service = new JwtTokenService(properties);
        assertNull(service.parseUsername(service.issue("admin")));
    }

    @Test
    void tamperedTokenReturnsNull() {
        JwtTokenService service = new JwtTokenService(properties);
        assertNull(service.parseUsername(service.issue("admin") + "x"));
    }

    @Test
    void garbageTokenReturnsNull() {
        JwtTokenService service = new JwtTokenService(properties);
        assertNull(service.parseUsername("not-a-jwt"));
    }
}

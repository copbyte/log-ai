package com.logmonitor.log.auth;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 密码校验测试：{noop} 明文、{bcrypt} 哈希、默认 BCrypt。
 */
class PasswordMatcherTest {

    private final PasswordMatcher matcher = new PasswordMatcher();

    @Test
    void noopPrefixMatchesPlaintext() {
        assertTrue(matcher.matches("123456", "{noop}123456"));
        assertFalse(matcher.matches("wrong", "{noop}123456"));
    }

    @Test
    void bcryptPrefixMatchesHash() {
        String hash = new BCryptPasswordEncoder().encode("abc123");
        assertTrue(matcher.matches("abc123", "{bcrypt}" + hash));
        assertFalse(matcher.matches("wrong", "{bcrypt}" + hash));
    }

    @Test
    void defaultTreatsAsBcrypt() {
        String hash = new BCryptPasswordEncoder().encode("abc123");
        assertTrue(matcher.matches("abc123", hash));
    }
}

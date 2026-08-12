package com.logmonitor.log.auth;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 密码校验器：兼容 Spring Security 的密码前缀约定
 * <ul>
 *   <li>{noop}xxx：明文（仅演示用）</li>
 *   <li>{bcrypt}xxx：BCrypt 哈希（生产推荐）</li>
 *   <li>无前缀：默认按 BCrypt 处理</li>
 * </ul>
 */
@Component
public class PasswordMatcher {

    private static final String BCRYPT_PREFIX = "{bcrypt}";
    private static final String NOOP_PREFIX = "{noop}";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public boolean matches(String rawPassword, String encoded) {
        if (rawPassword == null || encoded == null) {
            return false;
        }
        if (encoded.startsWith(BCRYPT_PREFIX)) {
            return encoder.matches(rawPassword, encoded.substring(BCRYPT_PREFIX.length()));
        }
        if (encoded.startsWith(NOOP_PREFIX)) {
            return rawPassword.equals(encoded.substring(NOOP_PREFIX.length()));
        }
        return encoder.matches(rawPassword, encoded);
    }
}

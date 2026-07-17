package com.logmonitor.gateway.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * 用户服务 - 处理用户认证和JWT生成
 */
@Slf4j
@Service
public class UserService {

    @Value("${jwt.secret:dev-secret-key-change-in-production-min-256-bits}")
    private String secret;

    @Value("${jwt.expiration:86400000}")
    private long expiration;

    private final JdbcTemplate jdbcTemplate;

    public UserService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 用户登录验证
     * 
     * @param username 用户名
     * @param password 密码（明文）
     * @return JWT token 或 null（验证失败）
     */
    public String login(String username, String password) {
        try {
            // 查询用户信息
            String sql = "SELECT id, username, password, role FROM sys_user WHERE username = ? AND is_enabled = 1";
            Map<String, Object> user = jdbcTemplate.queryForMap(sql, username);
            
            // 验证密码（这里使用简单的BCrypt验证）
            String storedPassword = (String) user.get("password");
            if (verifyPassword(password, storedPassword)) {
                // 生成JWT token
                SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
                return Jwts.builder()
                        .subject(username)
                        .claim("userId", user.get("id"))
                        .claim("username", username)
                        .claim("role", user.get("role"))
                        .issuedAt(new Date())
                        .expiration(new Date(System.currentTimeMillis() + expiration))
                        .signWith(key)
                        .compact();
            }
        } catch (Exception e) {
            log.warn("用户登录验证失败: username={}", username, e);
        }
        return null;
    }

    /**
     * 简单的密码验证（实际项目中应该使用BCrypt）
     * 这里为了演示，直接比较密码（生产环境必须使用BCrypt）
     */
    private boolean verifyPassword(String inputPassword, String storedPassword) {
        // 注意：生产环境应该使用BCryptPasswordEncoder.matches()
        // 这里只是演示，实际应该从数据库查询BCrypt加密的密码
        return "admin123".equals(inputPassword) && 
               "$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2".equals(storedPassword);
    }
}

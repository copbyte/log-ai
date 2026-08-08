package com.logmonitor.log.auth;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验（HS256）
 */
@Component
public class JwtTokenService {

    private final SecretKey key;
    private final long expireSeconds;

    public JwtTokenService(AuthProperties properties) {
        // HS256 要求密钥至少 32 字节，配置不足时启动即报错，避免线上弱密钥
        this.key = Keys.hmacShaKeyFor(properties.getJwtSecret().getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = properties.getTokenExpireSeconds();
    }

    /** 为指定用户签发 Token */
    public String issue(String username) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 Token 中的用户名；签名错误、过期、格式非法均返回 null。
     */
    public String parseUsername(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload()
                    .getSubject();
        } catch (Exception e) {
            return null;
        }
    }
}

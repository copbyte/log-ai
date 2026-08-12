package com.logmonitor.log.auth;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 认证配置（app.security.*）
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class AuthProperties {

    /** 认证开关：true 启用 JWT 认证；false 放行所有请求（本地演示降级） */
    private boolean enabled = false;

    /** JWT 签名密钥（HS256 要求至少 32 字节），生产用环境变量 JWT_SECRET 注入 */
    private String jwtSecret = "log-ai-demo-secret-change-me-in-production-2026";

    /** Token 有效期（秒） */
    private long tokenExpireSeconds = 7200;

    /** 服务间调用令牌（mcp-server 等内部客户端直接携带），生产用 LOG_SERVICE_TOKEN 注入 */
    private String serviceToken = "";

    /** 登录账号列表（密码支持 {noop} 明文 / {bcrypt} 哈希前缀） */
    private List<AuthUser> users = new ArrayList<>();

    @Data
    public static class AuthUser {
        private String username;
        private String password;
    }
}

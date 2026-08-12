package com.logmonitor.log.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * JWT 认证过滤器
 * <p>
 * 认证开启时，除登录接口和 CORS 预检外，所有请求必须携带有效 JWT 或服务间令牌，
 * 否则返回 401。认证用户写入 AuthContext（线程级），请求结束后清理。
 */
@Slf4j
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final AuthProperties properties;

    public JwtAuthFilter(JwtTokenService jwtTokenService, AuthProperties properties) {
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (!properties.isEnabled() || isWhitelisted(request)) {
                filterChain.doFilter(request, response);
                return;
            }

            String username = resolveUsername(request.getHeader(HttpHeaders.AUTHORIZATION));
            if (username == null) {
                writeUnauthorized(response);
                return;
            }
            AuthContext.setUsername(username);
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    /** 解析 Bearer Token：优先 JWT，其次服务间令牌（mcp-server 等内部客户端） */
    private String resolveUsername(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        String token = authorization.substring(7).trim();
        if (token.isEmpty()) {
            return null;
        }
        String username = jwtTokenService.parseUsername(token);
        if (username != null) {
            return username;
        }
        // 服务间令牌：常量时间比较，避免时序攻击
        String serviceToken = properties.getServiceToken();
        if (serviceToken != null && !serviceToken.isBlank()
                && MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        serviceToken.getBytes(StandardCharsets.UTF_8))) {
            return "service";
        }
        return null;
    }

    /** 白名单：登录接口 + CORS 预检 */
    private boolean isWhitelisted(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        // 健康检查供 K8s 探针/负载均衡使用，探针无法携带 JWT，必须放行
        return path.startsWith("/api/auth/login")
                || path.startsWith("/actuator/health")
                || "/error".equals(path);
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"未认证或登录已过期\",\"data\":null}");
    }
}

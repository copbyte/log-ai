package com.logmonitor.log.ratelimit;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * 限流拦截器：基于 Redis INCR + EXPIRE 的固定窗口计数。
 * <p>
 * 维度 = 用户（已认证）/ 客户端 IP（未认证，如登录接口）。
 * Redis 异常时放行（fail-open），避免限流组件故障导致全站不可用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final String KEY_PREFIX = "logai:ratelimit:";

    private final StringRedisTemplate redis;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        RateLimit rateLimit = handlerMethod.getMethodAnnotation(RateLimit.class);
        if (rateLimit == null) {
            return true;
        }

        String identity = AuthContext.getUsername() != null
                ? AuthContext.getUsername()
                : clientIp(request);
        String key = KEY_PREFIX
                + (rateLimit.key().isEmpty() ? handlerMethod.getMethod().getName() : rateLimit.key())
                + ":" + identity;

        try {
            Long count = redis.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redis.expire(key, Duration.ofSeconds(rateLimit.windowSeconds()));
            }
            if (count != null && count > rateLimit.limit()) {
                log.warn("接口限流触发: key={}, count={}, limit={}", key, count, rateLimit.limit());
                writeTooManyRequests(response);
                return false;
            }
        } catch (Exception e) {
            log.warn("限流检查失败，本次放行: {}", e.getMessage());
        }
        return true;
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletResponse response) throws Exception {
        response.setStatus(429);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSON.toJSONString(Result.fail(429, "请求过于频繁，请稍后再试")));
    }
}

package com.logmonitor.log.ratelimit;

import com.logmonitor.log.auth.AuthContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流拦截器测试：无注解放行、未超限放行、超限 429、Redis 故障放行、认证用户维度。
 */
class RateLimitInterceptorTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private RateLimitInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private HandlerMethod handler;
    private RateLimit rateLimit;

    @BeforeEach
    void setUp() throws Exception {
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);
        interceptor = new RateLimitInterceptor(redis);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        handler = mock(HandlerMethod.class);
        rateLimit = mock(RateLimit.class);
        when(rateLimit.limit()).thenReturn(3);
        when(rateLimit.windowSeconds()).thenReturn(10);
        when(rateLimit.key()).thenReturn("test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
    }

    @Test
    void withoutAnnotationAlwaysPasses() throws Exception {
        when(handler.getMethodAnnotation(RateLimit.class)).thenReturn(null);
        assertTrue(interceptor.preHandle(request, response, handler));
        verify(valueOps, never()).increment(anyString());
    }

    @Test
    void underLimitPassesAndSetsExpireOnFirstHit() throws Exception {
        when(handler.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
        when(valueOps.increment(anyString())).thenReturn(1L);
        assertTrue(interceptor.preHandle(request, response, handler));
        verify(redis).expire(anyString(), any(Duration.class));
    }

    @Test
    void overLimitReturns429AndRejects() throws Exception {
        when(handler.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
        when(valueOps.increment(anyString())).thenReturn(4L);
        assertFalse(interceptor.preHandle(request, response, handler));
        verify(response).setStatus(429);
    }

    @Test
    void redisFailureFailsOpen() throws Exception {
        when(handler.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
        when(valueOps.increment(anyString())).thenThrow(new RuntimeException("redis down"));
        assertTrue(interceptor.preHandle(request, response, handler));
    }

    @Test
    void authenticatedUserUsedAsIdentity() throws Exception {
        AuthContext.setUsername("admin");
        try {
            when(handler.getMethodAnnotation(RateLimit.class)).thenReturn(rateLimit);
            when(valueOps.increment(anyString())).thenReturn(1L);
            interceptor.preHandle(request, response, handler);
            verify(valueOps).increment("logai:ratelimit:test:admin");
        } finally {
            AuthContext.clear();
        }
    }
}

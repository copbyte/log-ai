package com.logmonitor.log.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JWT 过滤器测试：开关、白名单、缺失令牌 401、JWT 认证、服务令牌。
 */
class JwtAuthFilterTest {

    private AuthProperties properties;
    private JwtAuthFilter filter;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() throws Exception {
        properties = new AuthProperties();
        properties.setEnabled(true);
        properties.setJwtSecret("test-secret-key-with-at-least-32-bytes!!");
        properties.setTokenExpireSeconds(60);
        properties.setServiceToken("service-token-demo");
        filter = new JwtAuthFilter(new JwtTokenService(properties), properties);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
        chain = mock(FilterChain.class);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/log/entries");
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void disabledPassesThrough() throws Exception {
        properties.setEnabled(false);
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void loginPathIsWhitelisted() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/auth/login");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void actuatorHealthIsWhitelisted() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
    }

    @Test
    void missingTokenReturns401() throws Exception {
        filter.doFilter(request, response, chain);
        verify(response).setStatus(401);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void validJwtSetsAuthContextAndPasses() throws Exception {
        JwtTokenService jwtTokenService = new JwtTokenService(properties);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + jwtTokenService.issue("admin"));
        AtomicReference<String> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(AuthContext.getUsername());
            return null;
        }).when(chain).doFilter(any(), any());
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertEquals("admin", captured.get());
    }

    @Test
    void serviceTokenPassesAsServiceUser() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer service-token-demo");
        AtomicReference<String> captured = new AtomicReference<>();
        doAnswer(inv -> {
            captured.set(AuthContext.getUsername());
            return null;
        }).when(chain).doFilter(any(), any());
        filter.doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        assertEquals("service", captured.get());
    }
}

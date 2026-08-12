package com.logmonitor.log.controller;

import com.logmonitor.common.result.Result;
import com.logmonitor.log.audit.AuditLog;
import com.logmonitor.log.auth.AuthContext;
import com.logmonitor.log.auth.AuthException;
import com.logmonitor.log.auth.AuthService;
import com.logmonitor.log.auth.LoginRequest;
import com.logmonitor.log.auth.LoginResponse;
import com.logmonitor.log.ratelimit.RateLimit;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 认证接口：登录签发 JWT、查询当前用户
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @RateLimit(limit = 5, windowSeconds = 60, key = "login")
    @AuditLog(operation = "LOGIN")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/me")
    public Result<Map<String, String>> me() {
        String username = AuthContext.getUsername();
        if (username == null) {
            throw new AuthException("未认证");
        }
        return Result.success(Map.of("username", username));
    }
}

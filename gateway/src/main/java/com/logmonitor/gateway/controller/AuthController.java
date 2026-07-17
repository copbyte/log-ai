package com.logmonitor.gateway.controller;

import com.logmonitor.gateway.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器 - 提供简单的登录功能
 * <p>
 * 注意：这是一个简化版本，生产环境应该使用更安全的认证方式
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    /**
     * 用户登录接口
     * <p>
     * 请求体格式：
     * {
     *   "username": "admin",
     *   "password": "admin123"
     * }
     * 
     * 成功响应：
     * {
     *   "code": 200,
     *   "message": "success",
     *   "data": {
     *     "token": "eyJhbGciOiJIUzI1NiJ9..."
     *   }
     * }
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String username = credentials.get("username");
        String password = credentials.get("password");
        
        if (username == null || password == null) {
            return ResponseEntity.badRequest().body(createErrorResponse("用户名和密码不能为空"));
        }
        
        try {
            String token = userService.login(username, password);
            if (token != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("code", 200);
                response.put("message", "success");
                
                Map<String, Object> data = new HashMap<>();
                data.put("token", token);
                response.put("data", data);
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.badRequest().body(createErrorResponse("用户名或密码错误"));
            }
        } catch (Exception e) {
            log.error("登录失败", e);
            return ResponseEntity.internalServerError().body(createErrorResponse("登录服务异常"));
        }
    }
    
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("code", 400);
        response.put("message", message);
        return response;
    }
}

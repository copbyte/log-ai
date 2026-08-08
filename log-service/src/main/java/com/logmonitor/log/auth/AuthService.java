package com.logmonitor.log.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 登录认证服务：校验账号密码并签发 JWT。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthProperties properties;
    private final JwtTokenService jwtTokenService;
    private final PasswordMatcher passwordMatcher;

    public LoginResponse login(LoginRequest request) {
        if (request == null || !StringUtils.hasText(request.username()) || !StringUtils.hasText(request.password())) {
            throw new AuthException("用户名或密码不能为空");
        }
        AuthProperties.AuthUser user = findUser(request.username());
        if (user == null || !passwordMatcher.matches(request.password(), user.getPassword())) {
            throw new AuthException("用户名或密码错误");
        }
        String token = jwtTokenService.issue(user.getUsername());
        return new LoginResponse(token, properties.getTokenExpireSeconds(), user.getUsername());
    }

    private AuthProperties.AuthUser findUser(String username) {
        List<AuthProperties.AuthUser> users = properties.getUsers();
        if (users == null) {
            return null;
        }
        return users.stream()
                .filter(u -> u.getUsername() != null && u.getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }
}

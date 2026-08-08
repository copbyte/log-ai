package com.logmonitor.log.audit;

import com.alibaba.fastjson2.JSON;
import com.logmonitor.log.auth.AuthContext;
import com.logmonitor.log.security.SensitiveDataMasker;
import com.logmonitor.log.service.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 审计切面：拦截 @AuditLog 注解方法，记录操作人、参数摘要、结果与来源 IP。
 * <p>
 * 参数摘要统一脱敏（SensitiveDataMasker）并截断，避免敏感信息落库；
 * 审计失败不抛出，不影响业务（由 AuditLogService 内部兜底）。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditLogAspect {

    private static final int MAX_PARAMS_LENGTH = 2000;

    private final AuditLogService auditLogService;

    @Value("${app.audit.enabled:true}")
    private boolean enabled;

    @Around("@annotation(auditLog)")
    public Object around(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        if (!enabled) {
            return joinPoint.proceed();
        }

        String operation = auditLog.operation();
        String username = AuthContext.getUsername();
        String ip = clientIp();
        String params = summarize(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            auditLogService.record(operation, username, params, "SUCCESS", null, ip);
            return result;
        } catch (Throwable t) {
            auditLogService.record(operation, username, params, "FAIL", truncate(t.getMessage(), 500), ip);
            throw t;
        }
    }

    /** 参数摘要：JSON 序列化 + 脱敏 + 截断 */
    private String summarize(Object[] args) {
        if (args == null || args.length == 0) {
            return "";
        }
        String json;
        try {
            json = JSON.toJSONString(Arrays.asList(args));
        } catch (Exception e) {
            json = Arrays.toString(args);
        }
        return truncate(SensitiveDataMasker.mask(json), MAX_PARAMS_LENGTH);
    }

    private String clientIp() {
        try {
            RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
            if (attrs instanceof ServletRequestAttributes sra) {
                HttpServletRequest request = sra.getRequest();
                String forwarded = request.getHeader("X-Forwarded-For");
                if (forwarded != null && !forwarded.isBlank()) {
                    return forwarded.split(",")[0].trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.debug("获取客户端 IP 失败: {}", e.getMessage());
        }
        return null;
    }

    private String truncate(String str, int maxLen) {
        if (str == null) {
            return null;
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}

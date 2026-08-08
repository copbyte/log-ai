package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.logmonitor.common.entity.AuditLog;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.auth.AuthContext;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.security.SensitiveDataMasker;
import com.logmonitor.log.service.AuditLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 审计接口：查询审计日志（管理员）、接收服务方上报（mcp-server 等服务令牌调用）。
 */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    /** 服务方上报审计记录请求体 */
    public record AuditRecordRequest(String operation, String username, String params,
                                     String result, String errorMessage) {
    }

    private final AuditLogService auditLogService;

    public AuditController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    /** 分页查询审计日志（需 JWT 登录） */
    @GetMapping
    @RateLimit(limit = 30, windowSeconds = 10, key = "audit:page")
    public Result<IPage<AuditLog>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "operation", required = false) String operation,
            @RequestParam(name = "username", required = false) String username) {
        return Result.success(auditLogService.page(page, size, operation, username));
    }

    /** 上报审计记录（mcp-server 等服务方调用），参数自动脱敏 */
    @PostMapping
    @RateLimit(limit = 120, windowSeconds = 10, key = "audit:record")
    public Result<Void> record(@RequestBody AuditRecordRequest request) {
        String caller = AuthContext.getUsername();
        auditLogService.record(
                request.operation(),
                request.username() != null ? request.username() : caller,
                SensitiveDataMasker.mask(request.params()),
                request.result(),
                request.errorMessage(),
                caller);
        return Result.success();
    }
}

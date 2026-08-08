package com.logmonitor.log.controller;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.audit.AuditLog;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.service.LogEntryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志收集端点 — 供其他服务通过 SDK 上报日志
 */
@RestController
@RequestMapping("/api/log")
public class LogCollectController {

    private final LogEntryService logEntryService;

    public LogCollectController(LogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    /**
     * 批量接收其他服务上报的日志，直接入库
     */
    @PostMapping("/collect")
    @RateLimit(limit = 120, windowSeconds = 10, key = "log:collect")
    @AuditLog(operation = "LOG_COLLECT")
    public Result<Void> collect(@RequestBody List<LogEntry> entries) {
        logEntryService.saveBatch(entries, 100);
        return Result.success();
    }
}

package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.logmonitor.common.entity.Alert;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.audit.AuditLog;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.service.AlertService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 安全告警 REST API
 */
@RestController
@RequestMapping("/api/alert")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** 分页查询告警 */
    @GetMapping
    @RateLimit(limit = 60, windowSeconds = 10, key = "alert:page")
    public Result<IPage<Alert>> page(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "severity", required = false) Integer severity,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        return Result.success(alertService.listAlerts(status, severity, page, size));
    }

    /** 告警统计 */
    @GetMapping("/stats")
    @RateLimit(limit = 30, windowSeconds = 10, key = "alert:stats")
    public Result<Map<String, Object>> stats() {
        return Result.success(alertService.getAlertStats());
    }

    /** 查询单条告警 */
    @GetMapping("/{id}")
    public Result<Alert> getById(@PathVariable("id") Long id) {
        return Result.success(alertService.getAlert(id));
    }

    /** 确认告警 */
    @PutMapping("/{id}/ack")
    @AuditLog(operation = "ALERT_ACK")
    public Result<Void> ack(@PathVariable("id") Long id) {
        alertService.ackAlert(id);
        return Result.success();
    }

    /** 解决告警 */
    @PutMapping("/{id}/resolve")
    @AuditLog(operation = "ALERT_RESOLVE")
    public Result<Void> resolve(@PathVariable("id") Long id) {
        alertService.resolveAlert(id);
        return Result.success();
    }
}

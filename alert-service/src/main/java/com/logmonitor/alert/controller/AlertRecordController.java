package com.logmonitor.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.common.result.Result;
import com.logmonitor.alert.service.impl.AlertRecordServiceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/alert")
public class AlertRecordController {

    private final AlertRecordServiceImpl alertRecordService;

    public AlertRecordController(AlertRecordServiceImpl alertRecordService) {
        this.alertRecordService = alertRecordService;
    }

    @GetMapping("/records")
    public Result<IPage<AlertRecord>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "ruleId", required = false) Long ruleId,
            @RequestParam(name = "notifyStatus", required = false) String notifyStatus,
            @RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return Result.success(alertRecordService.pageWithFilters(
                new Page<>(page, size), ruleId, notifyStatus, startTime, endTime));
    }

    @GetMapping("/records/{id}")
    public Result<AlertRecord> getById(@PathVariable("id") Long id) {
        return Result.success(alertRecordService.getById(id));
    }
}

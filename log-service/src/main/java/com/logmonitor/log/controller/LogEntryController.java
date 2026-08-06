package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.service.LogEntryService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/log")
public class LogEntryController {

    private final LogEntryService logEntryService;

    public LogEntryController(LogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    @GetMapping("/entries")
    public Result<IPage<LogEntry>> page(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "logLevel", required = false) String logLevel,
            @RequestParam(name = "className", required = false) String className,
            @RequestParam(name = "fileName", required = false) String fileName,
            @RequestParam(name = "threadName", required = false) String threadName,
            @RequestParam(name = "startTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(name = "endTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "traceId", required = false) String traceId,
            @RequestParam(name = "serviceName", required = false) String serviceName,
            @RequestParam(name = "logSource", required = false) String logSource,
            @RequestParam(name = "srcIp", required = false) String srcIp,
            @RequestParam(name = "action", required = false) String action) {
        return Result.success(logEntryService.pageWithFilters(
                new Page<>(page, size), logLevel, className, fileName, threadName,
                startTime, endTime, keyword, traceId, serviceName, logSource, srcIp, action));
    }

    @GetMapping("/entries/{id}")
    public Result<LogEntry> getById(@PathVariable("id") Long id) {
        return Result.success(logEntryService.getById(id));
    }

    /** 按 TraceID 查询关联日志（链路日志聚合） */
    @GetMapping("/trace/{traceId}")
    public Result<List<LogEntry>> getByTraceId(@PathVariable("traceId") String traceId) {
        return Result.success(logEntryService.getLogsByTraceId(traceId));
    }
}

package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.service.impl.LogEntryServiceImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/log")
public class LogEntryController {

    private final LogEntryServiceImpl logEntryService;

    public LogEntryController(LogEntryServiceImpl logEntryService) {
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
            @RequestParam(name = "keyword", required = false) String keyword) {
        return Result.success(logEntryService.pageWithFilters(
                new Page<>(page, size), logLevel, className, fileName, threadName, startTime, endTime, keyword));
    }

    @GetMapping("/entries/{id}")
    public Result<LogEntry> getById(@PathVariable("id") Long id) {
        return Result.success(logEntryService.getById(id));
    }
}

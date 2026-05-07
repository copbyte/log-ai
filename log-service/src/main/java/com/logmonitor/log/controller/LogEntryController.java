package com.logmonitor.log.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.service.LogEntryService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/log")
public class LogEntryController {

    private final LogEntryService logEntryService;

    public LogEntryController(LogEntryService logEntryService) {
        this.logEntryService = logEntryService;
    }

    @GetMapping("/entries")
    public Result<IPage<LogEntry>> page(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return Result.success(logEntryService.page(new Page<>(page, size)));
    }

    @GetMapping("/entries/{id}")
    public Result<LogEntry> getById(@PathVariable Long id) {
        return Result.success(logEntryService.getById(id));
    }
}

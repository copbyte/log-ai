package com.logmonitor.log.controller;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.mq.producer.LogEntryProducer;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 日志收集端点 — 供其他服务通过 SDK 上报日志
 */
@RestController
@RequestMapping("/api/log")
public class LogCollectController {

    private final LogEntryProducer logEntryProducer;

    public LogCollectController(LogEntryProducer logEntryProducer) {
        this.logEntryProducer = logEntryProducer;
    }

    /**
     * 批量接收其他服务上报的日志，直接转发到 MQ
     */
    @PostMapping("/collect")
    public Result<Void> collect(@RequestBody List<LogEntry> entries) {
        for (LogEntry entry : entries) {
            logEntryProducer.send(entry);
        }
        return Result.success();
    }
}

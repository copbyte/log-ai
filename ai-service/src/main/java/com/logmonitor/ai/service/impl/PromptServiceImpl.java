package com.logmonitor.ai.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.ai.service.PromptService;
import org.springframework.stereotype.Service;

@Service
public class PromptServiceImpl implements PromptService {

    @Override
    public String buildPrompt(LogEntry logEntry) {
        return String.format("""
                Analyze the following log entry and provide:
                1. A brief summary of what happened
                2. The likely root cause
                3. A suggested action to resolve or investigate

                Log entry details:
                - File: %s
                - Level: %s
                - Time: %s
                - Thread: %s
                - Class: %s
                - Content: %s
                """,
                logEntry.getFileName(),
                logEntry.getLogLevel(),
                logEntry.getLogTime(),
                logEntry.getThreadName() != null ? logEntry.getThreadName() : "N/A",
                logEntry.getClassName() != null ? logEntry.getClassName() : "N/A",
                logEntry.getContent()
        );
    }
}

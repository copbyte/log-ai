package com.logmonitor.ai.service;

import com.logmonitor.common.entity.LogEntry;

public interface PromptService {

    String buildPrompt(LogEntry logEntry);
}

package com.logmonitor.ai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.logmonitor.common.entity.AiAnalysisResult;
import com.logmonitor.common.entity.LogEntry;

public interface AiAnalysisService extends IService<AiAnalysisResult> {

    AiAnalysisResult analyze(LogEntry logEntry, boolean force);
}

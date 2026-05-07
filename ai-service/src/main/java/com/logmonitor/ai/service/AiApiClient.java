package com.logmonitor.ai.service;

import com.logmonitor.ai.service.impl.AnalysisResponse;

public interface AiApiClient {

    AnalysisResponse analyze(String prompt);
}

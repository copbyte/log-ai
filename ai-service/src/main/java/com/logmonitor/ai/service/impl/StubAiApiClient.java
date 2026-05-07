package com.logmonitor.ai.service.impl;

import com.logmonitor.ai.service.AiApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class StubAiApiClient implements AiApiClient {

    @Override
    public AnalysisResponse analyze(String prompt) {
        log.info("Stub AI API called, prompt length: {} chars", prompt.length());

        AnalysisResponse response = new AnalysisResponse();
        response.setLevel("INFO");
        response.setSummary("Stub analysis - The log entry indicates normal system operation");
        response.setRootCause("Simulated root cause: No actual issue detected");
        response.setSuggestion("Continue monitoring. If issues persist, check the related service logs for more details");
        return response;
    }
}

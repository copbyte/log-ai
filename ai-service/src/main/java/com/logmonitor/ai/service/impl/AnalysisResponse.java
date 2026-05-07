package com.logmonitor.ai.service.impl;

import lombok.Data;

@Data
public class AnalysisResponse {

    private String level;
    private String summary;
    private String rootCause;
    private String suggestion;
}

package com.logmonitor.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AiAnalysisResultDTO {

    private Long id;
    private Long logEntryId;
    private String summary;
    private String rootCause;
    private String suggestion;
    private String modelName;
    private Integer tokensUsed;
    private LocalDateTime createTime;
}

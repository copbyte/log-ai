package com.logmonitor.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LogEntryDTO {

    private Long id;
    private String fileName;
    private String logLevel;
    private LocalDateTime logTime;
    private String content;
    private String threadName;
    private String className;
    private LocalDateTime createTime;
}

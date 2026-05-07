package com.logmonitor.common.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AlertRecordDTO {

    private Long id;
    private Long ruleId;
    private Long logEntryId;
    private String alertContent;
    private String notifyStatus;
    private LocalDateTime createTime;
}

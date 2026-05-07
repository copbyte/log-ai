package com.logmonitor.common.dto;

import com.logmonitor.common.enums.MatchType;
import com.logmonitor.common.enums.NotifyType;
import lombok.Data;

@Data
public class AlertRuleDTO {

    private Long id;
    private String ruleName;
    private String keyword;
    private String logLevel;
    private MatchType matchType;
    private NotifyType notifyType;
    private Boolean isEnabled;
}

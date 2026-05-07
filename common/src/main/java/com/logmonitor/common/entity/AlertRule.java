package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.logmonitor.common.enums.MatchType;
import com.logmonitor.common.enums.NotifyType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("alert_rule")
public class AlertRule extends BaseEntity {

    private String ruleName;
    private String keyword;
    private String logLevel;
    private MatchType matchType;
    private NotifyType notifyType;
    private Boolean isEnabled;
}

package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("alert_record")
public class AlertRecord extends BaseEntity {

    private Long ruleId;
    private String ruleName;
    private Long logEntryId;
    private String logLevel;
    private String alertContent;
    private String notifyType;
    private String notifyStatus;

    @TableField(exist = false)
    private LocalDateTime updateTime;
}

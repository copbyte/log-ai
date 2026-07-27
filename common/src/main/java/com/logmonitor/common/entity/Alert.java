package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 安全告警（规则触发产生）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("alert")
public class Alert extends BaseEntity {

    /** 触发的规则ID */
    private Long ruleId;
    /** 规则名称 */
    private String ruleName;
    /** 严重级别 0-10 */
    private Integer severity;
    /** 攻击源IP */
    private String srcIp;
    /** 关联日志ID */
    private Long logEntryId;
    /** 告警内容描述 */
    private String content;
    /** 状态: OPEN(待处理)/ACK(已确认)/RESOLVED(已解决) */
    private String status;
}

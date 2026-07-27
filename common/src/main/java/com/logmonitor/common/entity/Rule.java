package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 安全规则（态势感知规则引擎）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("rule")
public class Rule extends BaseEntity {

    /** 规则名称 */
    private String name;
    /** 规则描述 */
    private String description;
    /** 规则类型: THRESHOLD(阈值)/MATCH(匹配)/CORRELATION(关联) */
    private String ruleType;
    /** 匹配字段: src_ip/log_level/action/service_name */
    private String conditionField;
    /** 操作符: EQ/NE/CONTAINS/GT/LT */
    private String conditionOp;
    /** 匹配值 */
    private String conditionValue;
    /** 触发阈值（时间窗口内命中次数） */
    private Integer threshold;
    /** 时间窗口（秒） */
    private Integer timeWindowSec;
    /** 告警严重级别 0-10 */
    private Integer severity;
    /** 是否启用 */
    private Boolean enabled;
}

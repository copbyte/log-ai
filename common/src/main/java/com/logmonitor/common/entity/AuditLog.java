package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 安全审计日志
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLog extends BaseEntity {

    /** 操作用户（服务间调用为 service） */
    private String username;

    /** 操作类型：LOGIN/LOG_QUERY/ALERT_ACK/RULE_CREATE/AI_CHAT 等 */
    private String operation;

    /** 请求参数摘要（已脱敏、已截断） */
    private String params;

    /** 结果：SUCCESS / FAIL */
    private String result;

    /** 失败原因 */
    private String errorMessage;

    /** 来源 IP */
    private String ip;

    /** 审计日志为追加写，无更新时间列，不参与 MyBatis-Plus 字段映射 */
    @TableField(exist = false)
    private LocalDateTime updateTime;
}

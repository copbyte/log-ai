package com.logmonitor.log.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审计注解：标注在需要审计的接口/方法上，由 AuditLogAspect 记录
 * 操作人（AuthContext）、参数摘要、结果与来源 IP。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /** 操作类型，如 LOGIN / LOG_QUERY / ALERT_ACK / RULE_CREATE / AI_CHAT */
    String operation();
}

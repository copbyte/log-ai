package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 规则条件内存求值器（流式规则引擎用）
 * <p>
 * 与 RuleEngineService 的 SQL 条件映射保持同一套字段/操作符白名单语义：
 * EQ/NE 忽略大小写（对齐 MySQL 默认排序规则），CONTAINS 忽略大小写。
 */
@Component
public class RuleConditionEvaluator {

    /** 判断一条日志是否命中规则条件 */
    public boolean matches(Rule rule, LogEntry entry) {
        if (rule == null || entry == null) {
            return false;
        }
        String field = rule.getConditionField();
        String op = rule.getConditionOp();
        String value = rule.getConditionValue();
        if (!StringUtils.hasText(field) || !StringUtils.hasText(op) || !StringUtils.hasText(value)) {
            return false;
        }
        Object actual = resolve(entry, field);
        if (actual == null) {
            return false;
        }
        switch (op) {
            case "EQ":
                return string(actual).equalsIgnoreCase(value);
            case "NE":
                return !string(actual).equalsIgnoreCase(value);
            case "CONTAINS":
                return string(actual).toLowerCase().contains(value.toLowerCase());
            case "GT":
                return compareOrder(actual, value) > 0;
            case "LT":
                return compareOrder(actual, value) < 0;
            default:
                return false;
        }
    }

    /** 规则条件字段 → LogEntry 字段（白名单） */
    private Object resolve(LogEntry entry, String field) {
        switch (field) {
            case "src_ip":
                return entry.getSrcIp();
            case "dst_ip":
                return entry.getDstIp();
            case "log_level":
                return entry.getLogLevel();
            case "action":
                return entry.getAction();
            case "service_name":
                return entry.getServiceName();
            case "content":
                return entry.getContent();
            case "severity":
                return entry.getSeverity();
            case "src_port":
                return entry.getSrcPort();
            case "dst_port":
                return entry.getDstPort();
            default:
                return null;
        }
    }

    /** 数值比较（GT/LT）；无法解析时返回 0（不命中） */
    private int compareOrder(Object actual, String value) {
        try {
            double a = actual instanceof Number
                    ? ((Number) actual).doubleValue()
                    : Double.parseDouble(actual.toString());
            return Double.compare(a, Double.parseDouble(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String string(Object o) {
        return o == null ? "" : o.toString();
    }
}

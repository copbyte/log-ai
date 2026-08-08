package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 规则条件求值器测试：EQ/CONTAINS 忽略大小写、数值比较、未知字段。
 */
class RuleConditionEvaluatorTest {

    private final RuleConditionEvaluator evaluator = new RuleConditionEvaluator();

    @Test
    void eqIgnoresCase() {
        Rule rule = Rule.builder()
                .conditionField("action").conditionOp("EQ").conditionValue("deny").build();
        LogEntry entry = LogEntry.builder().action("DENY").build();
        assertTrue(evaluator.matches(rule, entry));
    }

    @Test
    void containsIgnoresCase() {
        Rule rule = Rule.builder()
                .conditionField("content").conditionOp("CONTAINS").conditionValue("port scan").build();
        LogEntry entry = LogEntry.builder().content("Port Scan detected from 10.0.0.5").build();
        assertTrue(evaluator.matches(rule, entry));
    }

    @Test
    void gtOnSeverity() {
        Rule rule = Rule.builder()
                .conditionField("severity").conditionOp("GT").conditionValue("7").build();
        assertTrue(evaluator.matches(rule, LogEntry.builder().severity(8).build()));
        assertFalse(evaluator.matches(rule, LogEntry.builder().severity(6).build()));
    }

    @Test
    void unknownFieldReturnsFalse() {
        Rule rule = Rule.builder()
                .conditionField("hacker_ip").conditionOp("EQ").conditionValue("1.2.3.4").build();
        assertFalse(evaluator.matches(rule, LogEntry.builder().srcIp("1.2.3.4").build()));
    }

    @Test
    void nullFieldValueReturnsFalse() {
        Rule rule = Rule.builder()
                .conditionField("src_ip").conditionOp("EQ").conditionValue("1.2.3.4").build();
        assertFalse(evaluator.matches(rule, LogEntry.builder().srcIp(null).build()));
    }
}

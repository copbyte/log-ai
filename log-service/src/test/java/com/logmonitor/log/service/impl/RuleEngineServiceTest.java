package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.logmonitor.common.entity.Alert;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import com.logmonitor.log.mapper.AlertMapper;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.mapper.RuleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 规则引擎单元测试：验证 MATCH 去重、THRESHOLD SQL 聚合与窗口去重。
 */
class RuleEngineServiceTest {

    private RuleMapper ruleMapper;
    private LogEntryMapper logEntryMapper;
    private AlertMapper alertMapper;
    private RuleEngineService service;

    @BeforeEach
    void setUp() {
        ruleMapper = mock(RuleMapper.class);
        logEntryMapper = mock(LogEntryMapper.class);
        alertMapper = mock(AlertMapper.class);
        service = new RuleEngineService(ruleMapper, logEntryMapper, alertMapper);
        // 注册实体 TableInfo，否则 LambdaQueryWrapper 在无 Spring 上下文的单测中报
        // "can not find lambda cache for this entity"
        initTableInfo(Rule.class);
        initTableInfo(LogEntry.class);
        initTableInfo(Alert.class);
    }

    private static void initTableInfo(Class<?> entityClass) {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityClass);
    }

    @Test
    void matchRuleSkipsAlreadyAlertedLogEntries() {
        Rule rule = Rule.builder()
                .name("SQL注入检测").ruleType("MATCH")
                .conditionField("content").conditionOp("CONTAINS").conditionValue("union select")
                .timeWindowSec(60).severity(8).enabled(true).build();
        rule.setId(1L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        LogEntry e1 = LogEntry.builder().srcIp("10.0.0.5").content("union select 1")
                .logTime(LocalDateTime.now()).build();
        e1.setId(1L);
        LogEntry e2 = LogEntry.builder().srcIp("10.0.0.6").content("union select 2")
                .logTime(LocalDateTime.now()).build();
        e2.setId(2L);
        when(logEntryMapper.selectList(any())).thenReturn(List.of(e1, e2));

        // e1 已告警过，e2 未告警
        Alert existing = Alert.builder().ruleId(1L).logEntryId(1L).build();
        when(alertMapper.selectList(any())).thenReturn(List.of(existing));

        service.executeRules();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper, times(1)).insert(captor.capture());
        assertEquals(2L, captor.getValue().getLogEntryId());
        assertEquals("10.0.0.6", captor.getValue().getSrcIp());
    }

    @Test
    void thresholdRuleInsertsOneAlertPerQualifyingGroup() {
        Rule rule = Rule.builder()
                .name("暴力破解检测").ruleType("THRESHOLD")
                .conditionField("action").conditionOp("EQ").conditionValue("deny")
                .threshold(5).timeWindowSec(60).severity(7).enabled(true).build();
        rule.setId(2L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));

        // SQL 聚合（HAVING COUNT >= threshold）只返回达标分组
        when(logEntryMapper.countGroupBySrcIp(any(), eq("action"), eq("="), eq("deny"), eq(5)))
                .thenReturn(List.of(
                        Map.of("srcIp", "10.0.0.5", "cnt", 6L),
                        Map.of("srcIp", "10.0.0.7", "cnt", 9L)));
        when(alertMapper.selectCount(any())).thenReturn(0L);

        service.executeRules();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper, times(2)).insert(captor.capture());
        assertEquals(
                List.of("10.0.0.5", "10.0.0.7"),
                captor.getAllValues().stream().map(Alert::getSrcIp).toList());
        assertNull(captor.getAllValues().get(0).getLogEntryId());
        assertEquals("OPEN", captor.getAllValues().get(0).getStatus());
    }

    @Test
    void thresholdRuleSkipsSrcIpAlreadyAlertedInWindow() {
        Rule rule = Rule.builder()
                .name("暴力破解检测").ruleType("THRESHOLD")
                .conditionField("action").conditionOp("EQ").conditionValue("deny")
                .threshold(5).timeWindowSec(60).severity(7).enabled(true).build();
        rule.setId(2L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(logEntryMapper.countGroupBySrcIp(any(), eq("action"), eq("="), eq("deny"), eq(5)))
                .thenReturn(List.of(Map.of("srcIp", "10.0.0.5", "cnt", 8L)));
        // 窗口内已有该 IP 的告警 -> 不再重复告警
        when(alertMapper.selectCount(any())).thenReturn(1L);

        service.executeRules();

        verify(alertMapper, never()).insert(any(Alert.class));
    }

    @Test
    void containsConditionBuildsLikeValue() {
        Rule rule = Rule.builder()
                .name("XSS检测").ruleType("THRESHOLD")
                .conditionField("content").conditionOp("CONTAINS").conditionValue("<script>")
                .threshold(1).timeWindowSec(60).severity(8).enabled(true).build();
        rule.setId(3L);
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(logEntryMapper.countGroupBySrcIp(any(), eq("content"), eq("LIKE"), eq("%<script>%"), eq(1)))
                .thenReturn(List.of());
        when(alertMapper.selectCount(any())).thenReturn(0L);

        service.executeRules();

        verify(logEntryMapper).countGroupBySrcIp(any(), eq("content"), eq("LIKE"), eq("%<script>%"), eq(1));
        verify(alertMapper, never()).insert(any(Alert.class));
    }
}

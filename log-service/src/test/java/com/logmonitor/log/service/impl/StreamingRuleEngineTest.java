package com.logmonitor.log.service.impl;

import com.logmonitor.common.entity.Alert;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import com.logmonitor.log.mapper.AlertMapper;
import com.logmonitor.log.mapper.RuleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 流式规则引擎测试：MATCH 去重、THRESHOLD 滑动窗口计数、窗口去重与开关控制。
 */
class StreamingRuleEngineTest {

    private RuleMapper ruleMapper;
    private AlertMapper alertMapper;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> valueOps;
    private ZSetOperations<String, String> zsetOps;
    private StreamingRuleEngine engine;

    @BeforeEach
    void setUp() throws Exception {
        ruleMapper = mock(RuleMapper.class);
        alertMapper = mock(AlertMapper.class);
        redis = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        zsetOps = mock(ZSetOperations.class);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zsetOps);
        when(redis.expire(anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        engine = new StreamingRuleEngine(ruleMapper, alertMapper, redis, new RuleConditionEvaluator());
        setEnabled(engine, true);
    }

    private static void setEnabled(StreamingRuleEngine engine, boolean value) throws Exception {
        Field f = StreamingRuleEngine.class.getDeclaredField("enabled");
        f.setAccessible(true);
        f.set(engine, value);
    }

    private void loadRules(Rule... rules) {
        when(ruleMapper.selectList(any())).thenReturn(List.of(rules));
        engine.refreshRules();
    }

    @Test
    void matchRuleInsertsAlertOncePerLogEntry() {
        Rule rule = Rule.builder()
                .name("SQL注入检测").ruleType("MATCH")
                .conditionField("content").conditionOp("CONTAINS").conditionValue("union select")
                .timeWindowSec(60).severity(8).enabled(true).build();
        rule.setId(1L);
        loadRules(rule);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(Boolean.TRUE, Boolean.FALSE);

        LogEntry entry = LogEntry.builder().srcIp("10.0.0.5").content("union select 1").build();
        entry.setId(100L);
        // 同一日志重复投递（至少一次语义）只应产生一条告警
        engine.processBatch(List.of(entry, entry));

        verify(alertMapper, times(1)).insert(any(Alert.class));
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOps, times(2)).setIfAbsent(keyCaptor.capture(), anyString(), any(Duration.class));
        assertEquals("logai:rule:match:1:100", keyCaptor.getAllValues().get(0));
    }

    @Test
    void thresholdRuleInsertsAlertWhenWindowCountReached() {
        Rule rule = Rule.builder()
                .name("暴力破解检测").ruleType("THRESHOLD")
                .conditionField("action").conditionOp("EQ").conditionValue("deny")
                .threshold(5).timeWindowSec(60).severity(7).enabled(true).build();
        rule.setId(2L);
        loadRules(rule);
        when(zsetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Boolean.TRUE);
        when(zsetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L);
        when(zsetOps.zCard(anyString())).thenReturn(5L);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.TRUE);

        LogEntry entry = LogEntry.builder().srcIp("10.0.0.5").action("deny").build();
        entry.setId(1L);
        engine.processBatch(List.of(entry));

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertMapper, times(1)).insert(captor.capture());
        assertEquals("10.0.0.5", captor.getValue().getSrcIp());
        assertNull(captor.getValue().getLogEntryId());
        assertEquals("OPEN", captor.getValue().getStatus());
        verify(zsetOps).removeRangeByScore(anyString(), anyDouble(), anyDouble());
        verify(valueOps).setIfAbsent(eq("logai:rule:alert:2:10.0.0.5"), anyString(), any(Duration.class));
    }

    @Test
    void thresholdRuleSkipsWhenBelowThreshold() {
        Rule rule = Rule.builder()
                .name("暴力破解检测").ruleType("THRESHOLD")
                .conditionField("action").conditionOp("EQ").conditionValue("deny")
                .threshold(5).timeWindowSec(60).severity(7).enabled(true).build();
        rule.setId(2L);
        loadRules(rule);
        when(zsetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Boolean.TRUE);
        when(zsetOps.removeRangeByScore(anyString(), anyDouble(), anyDouble())).thenReturn(1L);
        when(zsetOps.zCard(anyString())).thenReturn(3L);

        LogEntry entry = LogEntry.builder().srcIp("10.0.0.5").action("deny").build();
        entry.setId(1L);
        engine.processBatch(List.of(entry));

        verify(alertMapper, never()).insert(any(Alert.class));
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void thresholdRuleSkipsWhenAlreadyAlertedInWindow() {
        Rule rule = Rule.builder()
                .name("暴力破解检测").ruleType("THRESHOLD")
                .conditionField("action").conditionOp("EQ").conditionValue("deny")
                .threshold(5).timeWindowSec(60).severity(7).enabled(true).build();
        rule.setId(2L);
        loadRules(rule);
        when(zsetOps.add(anyString(), anyString(), anyDouble())).thenReturn(Boolean.TRUE);
        when(zsetOps.zCard(anyString())).thenReturn(5L);
        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(Boolean.FALSE);

        LogEntry entry = LogEntry.builder().srcIp("10.0.0.5").action("deny").build();
        entry.setId(1L);
        engine.processBatch(List.of(entry));

        verify(alertMapper, never()).insert(any(Alert.class));
    }

    @Test
    void disabledEngineDoesNothing() throws Exception {
        setEnabled(engine, false);
        loadRules(Rule.builder()
                .name("SQL注入检测").ruleType("MATCH")
                .conditionField("content").conditionOp("CONTAINS").conditionValue("union select")
                .timeWindowSec(60).severity(8).enabled(true).build());

        LogEntry entry = LogEntry.builder().content("union select 1").build();
        entry.setId(1L);
        engine.processBatch(List.of(entry));

        verifyNoInteractions(redis);
        verify(alertMapper, never()).insert(any(Alert.class));
    }
}

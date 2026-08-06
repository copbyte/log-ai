package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.logmonitor.common.entity.Alert;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import com.logmonitor.log.mapper.AlertMapper;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.mapper.RuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则引擎核心：定时扫描日志，匹配规则，生成告警
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleEngineService {

    private final RuleMapper ruleMapper;
    private final LogEntryMapper logEntryMapper;
    private final AlertMapper alertMapper;

    /** 每分钟执行一次规则匹配 */
    @Scheduled(fixedDelay = 60000)
    public void executeRules() {
        log.info("规则引擎开始执行...");

        // 查询所有启用的规则
        LambdaQueryWrapper<Rule> ruleWrapper = new LambdaQueryWrapper<>();
        ruleWrapper.eq(Rule::getEnabled, true);
        List<Rule> rules = ruleMapper.selectList(ruleWrapper);

        int totalAlerts = 0;
        for (Rule rule : rules) {
            try {
                totalAlerts += executeRule(rule);
            } catch (Exception e) {
                log.error("执行规则[{}]出错: {}", rule.getName(), e.getMessage(), e);
            }
        }

        log.info("规则引擎执行完成，共生成 {} 条告警", totalAlerts);
    }

    /** 根据规则类型执行不同匹配逻辑 */
    private int executeRule(Rule rule) {
        String ruleType = rule.getRuleType();
        if ("MATCH".equals(ruleType)) {
            return executeMatchRule(rule);
        } else if ("THRESHOLD".equals(ruleType)) {
            return executeThresholdRule(rule);
        }
        log.warn("未知规则类型: {}（规则: {}）", ruleType, rule.getName());
        return 0;
    }

    /**
     * MATCH 类型：查询时间窗口内匹配条件的日志，每条匹配日志生成一条 Alert
     * 去重：同一规则+同一 srcIp+同一 logEntryId 不重复告警
     */
    private int executeMatchRule(Rule rule) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(rule.getTimeWindowSec());
        LambdaQueryWrapper<LogEntry> wrapper = buildLogQueryWrapper(rule, since);
        List<LogEntry> matchedLogs = logEntryMapper.selectList(wrapper);

        int alertCount = 0;
        for (LogEntry logEntry : matchedLogs) {
            String srcIp = logEntry.getSrcIp();
            // 去重：同一规则+同一 srcIp+同一 logEntryId 不重复告警
            if (hasAlert(rule.getId(), srcIp, logEntry.getId())) {
                continue;
            }
            Alert alert = buildAlert(rule, srcIp, logEntry.getId(),
                    String.format("规则[%s]匹配到日志: %s", rule.getName(), truncate(logEntry.getContent(), 200)));
            alertMapper.insert(alert);
            alertCount++;
        }
        return alertCount;
    }

    /**
     * THRESHOLD 类型：查询时间窗口内匹配条件的日志数量（按 srcIp 分组），
     * 若某 srcIp 命中次数 >= threshold，则生成一条汇总告警
     * 去重：同一规则+同一 srcIp 在时间窗口内不重复告警
     */
    private int executeThresholdRule(Rule rule) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(rule.getTimeWindowSec());
        LambdaQueryWrapper<LogEntry> wrapper = buildLogQueryWrapper(rule, since);
        List<LogEntry> matchedLogs = logEntryMapper.selectList(wrapper);

        // 按 srcIp 分组统计
        Map<String, Integer> srcIpCount = new HashMap<>();
        for (LogEntry logEntry : matchedLogs) {
            String srcIp = logEntry.getSrcIp() != null ? logEntry.getSrcIp() : "unknown";
            srcIpCount.merge(srcIp, 1, Integer::sum);
        }

        int alertCount = 0;
        for (Map.Entry<String, Integer> entry : srcIpCount.entrySet()) {
            String srcIp = entry.getKey();
            int count = entry.getValue();
            if (count < rule.getThreshold()) {
                continue;
            }
            // 去重：同一规则+同一 srcIp 在时间窗口内不重复告警
            if (hasAlertInWindow(rule.getId(), srcIp, rule.getTimeWindowSec())) {
                continue;
            }
            Alert alert = buildAlert(rule, srcIp, null,
                    String.format("规则[%s]阈值告警: 源IP %s 在 %d 秒内触发 %d 次（阈值 %d）",
                            rule.getName(), srcIp, rule.getTimeWindowSec(), count, rule.getThreshold()));
            alertMapper.insert(alert);
            alertCount++;
        }
        return alertCount;
    }

    /** 构建 LogEntry 查询条件：时间窗口 + 规则条件 */
    private LambdaQueryWrapper<LogEntry> buildLogQueryWrapper(Rule rule, LocalDateTime since) {
        LambdaQueryWrapper<LogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(LogEntry::getLogTime, since);
        applyCondition(wrapper, rule.getConditionField(), rule.getConditionOp(), rule.getConditionValue());
        return wrapper;
    }

    /** 应用规则条件到查询 wrapper（conditionField → LogEntry 字段映射） */
    private void applyCondition(LambdaQueryWrapper<LogEntry> wrapper, String field, String op, String value) {
        if (!StringUtils.hasText(field) || !StringUtils.hasText(op) || !StringUtils.hasText(value)) {
            return;
        }
        switch (field) {
            case "src_ip":
                applyOp(wrapper, LogEntry::getSrcIp, op, value);
                break;
            case "log_level":
                applyOp(wrapper, LogEntry::getLogLevel, op, value);
                break;
            case "action":
                applyOp(wrapper, LogEntry::getAction, op, value);
                break;
            case "service_name":
                applyOp(wrapper, LogEntry::getServiceName, op, value);
                break;
            case "content":
                applyOp(wrapper, LogEntry::getContent, op, value);
                break;
            default:
                log.warn("未知条件字段: {}", field);
        }
    }

    /** 根据操作符应用条件 */
    private void applyOp(LambdaQueryWrapper<LogEntry> wrapper,
                         SFunction<LogEntry, ?> column, String op, String value) {
        switch (op) {
            case "EQ":
                wrapper.eq(column, value);
                break;
            case "NE":
                wrapper.ne(column, value);
                break;
            case "CONTAINS":
                wrapper.like(column, value);
                break;
            case "GT":
                wrapper.gt(column, value);
                break;
            case "LT":
                wrapper.lt(column, value);
                break;
            default:
                log.warn("未知操作符: {}", op);
        }
    }

    /** 检查是否已有告警（MATCH 去重：rule + srcIp + logEntryId） */
    private boolean hasAlert(Long ruleId, String srcIp, Long logEntryId) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getRuleId, ruleId);
        if (srcIp != null) {
            wrapper.eq(Alert::getSrcIp, srcIp);
        }
        if (logEntryId != null) {
            wrapper.eq(Alert::getLogEntryId, logEntryId);
        }
        return alertMapper.selectCount(wrapper) > 0;
    }

    /** 检查时间窗口内是否已有告警（THRESHOLD 去重：rule + srcIp） */
    private boolean hasAlertInWindow(Long ruleId, String srcIp, int windowSec) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(windowSec);
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Alert::getRuleId, ruleId);
        if (srcIp != null) {
            wrapper.eq(Alert::getSrcIp, srcIp);
        }
        wrapper.ge(Alert::getCreateTime, since);
        return alertMapper.selectCount(wrapper) > 0;
    }

    /** 构建 Alert 对象 */
    private Alert buildAlert(Rule rule, String srcIp, Long logEntryId, String content) {
        return Alert.builder()
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .severity(rule.getSeverity())
                .srcIp(srcIp)
                .logEntryId(logEntryId)
                .content(content)
                .status("OPEN")
                .build();
    }

    /** 截断字符串 */
    private String truncate(String str, int maxLen) {
        if (str == null) {
            return "";
        }
        return str.length() > maxLen ? str.substring(0, maxLen) : str;
    }
}

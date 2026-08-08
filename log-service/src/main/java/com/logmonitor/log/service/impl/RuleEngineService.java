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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 规则引擎核心：定时扫描日志，匹配规则，生成告警
 * <p>
 * 性能优化：
 * <ul>
 *   <li>MATCH：一次批量查询该规则已有告警的 logEntryId 做内存去重，避免逐条 SELECT COUNT（原 N+1）；</li>
 *   <li>THRESHOLD：聚合下推 SQL（GROUP BY src_ip + HAVING COUNT &gt;= threshold），不再把窗口内全部日志加载到内存；</li>
 *   <li>幂等：alert 表 (rule_id, log_entry_id) 唯一索引兜底，防止并发/重试产生重复告警。</li>
 * </ul>
 * <p>
 * 启用流式规则引擎（log.rule-engine.streaming.enabled=true）时本类不创建，
 * 告警由 StreamingRuleEngine 在日志入库后实时判定；此处作为每分钟 SQL 扫描的降级方案。
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "log.rule-engine.streaming.enabled", havingValue = "false", matchIfMissing = true)
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
     * MATCH 类型：查询时间窗口内匹配条件的日志，每条匹配日志生成一条 Alert。
     * 去重：一次性加载该规则已有告警的 logEntryId，内存过滤，避免逐条 COUNT 查询。
     */
    private int executeMatchRule(Rule rule) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(rule.getTimeWindowSec());
        List<LogEntry> matchedLogs = logEntryMapper.selectList(buildLogQueryWrapper(rule, since));
        if (matchedLogs.isEmpty()) {
            return 0;
        }

        Set<Long> alertedLogEntryIds = alertMapper.selectList(
                        new LambdaQueryWrapper<Alert>()
                                .eq(Alert::getRuleId, rule.getId())
                                .isNotNull(Alert::getLogEntryId)
                                .select(Alert::getLogEntryId))
                .stream()
                .map(Alert::getLogEntryId)
                .collect(Collectors.toSet());

        int alertCount = 0;
        for (LogEntry logEntry : matchedLogs) {
            if (alertedLogEntryIds.contains(logEntry.getId())) {
                continue;
            }
            Alert alert = buildAlert(rule, logEntry.getSrcIp(), logEntry.getId(),
                    String.format("规则[%s]匹配到日志: %s", rule.getName(), truncate(logEntry.getContent(), 200)));
            alertMapper.insert(alert);
            alertCount++;
        }
        return alertCount;
    }

    /**
     * THRESHOLD 类型：按 srcIp 分组统计窗口内命中次数，次数 &gt;= threshold 生成汇总告警。
     * 聚合下推 SQL（GROUP BY + HAVING），只把分组结果加载到内存。
     */
    private int executeThresholdRule(Rule rule) {
        LocalDateTime since = LocalDateTime.now().minusSeconds(rule.getTimeWindowSec());
        String column = toColumn(rule.getConditionField());
        String op = toSqlOp(rule.getConditionOp());
        String value = rule.getConditionValue();
        if (column == null || op == null || !StringUtils.hasText(value)) {
            log.warn("规则[{}]条件无效: field={}, op={}, value={}",
                    rule.getName(), rule.getConditionField(), rule.getConditionOp(), value);
            return 0;
        }
        if ("LIKE".equals(op)) {
            value = "%" + value + "%";
        }

        List<Map<String, Object>> grouped =
                logEntryMapper.countGroupBySrcIp(since, column, op, value, rule.getThreshold());

        int alertCount = 0;
        for (Map<String, Object> row : grouped) {
            Object srcIpObj = row.get("srcIp");
            String srcIp = srcIpObj != null ? String.valueOf(srcIpObj) : "unknown";
            long count = ((Number) row.get("cnt")).longValue();
            // 去重：同一规则 + 同一 srcIp 在时间窗口内不重复告警
            if (hasAlertInWindow(rule.getId(), srcIp, rule.getTimeWindowSec())) {
                continue;
            }
            Alert alert = buildAlert(rule, srcIp, null,
                    String.format("规则[%s]阈值告警: 源IP %s 在 %d 秒内触发 %d 次（阈值:%d）",
                            rule.getName(), srcIp, rule.getTimeWindowSec(), count, rule.getThreshold()));
            alertMapper.insert(alert);
            alertCount++;
        }
        return alertCount;
    }

    /** 构建 LogEntry 查询条件：时间窗口 + 规则条件（MATCH 用） */
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

    /** 规则条件字段 → SQL 列名（白名单映射，防止 SQL 注入） */
    private String toColumn(String field) {
        switch (field) {
            case "src_ip":
            case "log_level":
            case "action":
            case "service_name":
            case "content":
                return field;
            default:
                return null;
        }
    }

    /** 规则操作符 → SQL 操作符（白名单映射） */
    private String toSqlOp(String op) {
        switch (op) {
            case "EQ":
                return "=";
            case "NE":
                return "<>";
            case "CONTAINS":
                return "LIKE";
            case "GT":
                return ">";
            case "LT":
                return "<";
            default:
                return null;
        }
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

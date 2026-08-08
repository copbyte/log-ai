package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.logmonitor.common.entity.Alert;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.entity.Rule;
import com.logmonitor.log.mapper.AlertMapper;
import com.logmonitor.log.mapper.RuleMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 流式规则引擎（Redis 窗口计数）
 * <p>
 * 日志入库后的异步后置处理中逐条评估规则（由 LogBatchProcessor 调用）：
 * <ul>
 *   <li>MATCH：Redis SETNX 按（rule_id, log_entry_id）幂等去重，命中即生成单条告警；</li>
 *   <li>THRESHOLD：Redis ZSET 滑动窗口计数（rule_id + src_ip），窗口内达到阈值生成汇总告警；</li>
 * </ul>
 * 相比每分钟扫表的 SQL 规则引擎，日志到达时实时判定，适合高频告警场景。
 * 高吞吐场景可将多次 Redis 操作合并为 pipeline / Lua 脚本减少网络往返。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamingRuleEngine {

    private static final String KEY_PREFIX = "logai:rule:";

    private final RuleMapper ruleMapper;
    private final AlertMapper alertMapper;
    private final StringRedisTemplate redis;
    private final RuleConditionEvaluator conditionEvaluator;

    @Value("${log.rule-engine.streaming.enabled:false}")
    private boolean enabled;

    /** 启用规则缓存，定时刷新，避免每条日志都查规则表 */
    private volatile List<Rule> rules = List.of();

    @PostConstruct
    public void init() {
        refreshRules();
    }

    /** 定时刷新启用规则缓存，规则变更最多一个周期后生效 */
    @Scheduled(fixedDelayString = "${log.rule-engine.streaming.rule-refresh-interval-ms:30000}")
    public void refreshRules() {
        try {
            LambdaQueryWrapper<Rule> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(Rule::getEnabled, true);
            this.rules = ruleMapper.selectList(wrapper);
            log.info("流式规则引擎已加载 {} 条启用规则", rules.size());
        } catch (Exception e) {
            log.error("流式规则引擎加载规则失败: {}", e.getMessage());
        }
    }

    /** 处理一批已入库日志（由 LogBatchProcessor 异步调用） */
    public void processBatch(List<LogEntry> entries) {
        if (!enabled || entries == null || entries.isEmpty()) {
            return;
        }
        List<Rule> snapshot = rules;
        if (snapshot.isEmpty()) {
            return;
        }
        for (LogEntry entry : entries) {
            for (Rule rule : snapshot) {
                try {
                    evaluate(rule, entry);
                } catch (Exception e) {
                    log.error("流式规则[{}]处理日志[{}]失败: {}", rule.getName(), entry.getId(), e.getMessage());
                }
            }
        }
    }

    private void evaluate(Rule rule, LogEntry entry) {
        if (!conditionEvaluator.matches(rule, entry)) {
            return;
        }
        String ruleType = rule.getRuleType();
        if ("MATCH".equals(ruleType)) {
            handleMatch(rule, entry);
        } else if ("THRESHOLD".equals(ruleType)) {
            handleThreshold(rule, entry);
        }
    }

    /** MATCH：Redis SETNX 按（rule_id, log_entry_id）幂等去重后生成单条告警 */
    private void handleMatch(Rule rule, LogEntry entry) {
        if (entry.getId() == null) {
            // 无主键无法幂等去重（正常入库流程会回填 ID），跳过
            return;
        }
        long windowSec = windowSec(rule);
        String dedupKey = KEY_PREFIX + "match:" + rule.getId() + ":" + entry.getId();
        Boolean first = redis.opsForValue()
                .setIfAbsent(dedupKey, "1", Duration.ofSeconds(windowSec));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        Alert alert = buildAlert(rule, entry.getSrcIp(), entry.getId(),
                String.format("规则[%s]匹配到日志: %s", rule.getName(), truncate(entry.getContent(), 200)));
        alertMapper.insert(alert);
    }

    /** THRESHOLD：ZSET 滑动窗口计数，窗口内达到阈值生成汇总告警 */
    private void handleThreshold(Rule rule, LogEntry entry) {
        String srcIp = entry.getSrcIp() != null ? entry.getSrcIp() : "unknown";
        long windowSec = windowSec(rule);
        long nowMs = System.currentTimeMillis();
        String windowKey = KEY_PREFIX + "win:" + rule.getId() + ":" + srcIp;

        ZSetOperations<String, String> zset = redis.opsForZSet();
        // 成员用日志主键，同一日志重复投递不会重复计数
        String member = entry.getId() != null
                ? "e" + entry.getId()
                : "t" + nowMs + "-" + System.nanoTime();
        zset.add(windowKey, member, nowMs);
        // 移除窗口外的旧成员（滑动窗口）
        zset.removeRangeByScore(windowKey, Double.NEGATIVE_INFINITY, nowMs - windowSec * 1000.0);
        Long count = zset.zCard(windowKey);
        // 窗口键兜底过期，防止长期不触发的 IP 残留
        redis.expire(windowKey, Duration.ofSeconds(windowSec * 2));

        int threshold = rule.getThreshold() != null ? rule.getThreshold() : 1;
        if (count == null || count < threshold) {
            return;
        }
        // 窗口内同一（rule_id, src_ip）只告警一次
        String dedupKey = KEY_PREFIX + "alert:" + rule.getId() + ":" + srcIp;
        Boolean first = redis.opsForValue()
                .setIfAbsent(dedupKey, String.valueOf(nowMs), Duration.ofSeconds(windowSec));
        if (!Boolean.TRUE.equals(first)) {
            return;
        }
        Alert alert = buildAlert(rule, srcIp, null,
                String.format("规则[%s]阈值告警: 源IP %s 在 %d 秒内触发 %d 次（阈值:%d）",
                        rule.getName(), srcIp, windowSec, count, threshold));
        alertMapper.insert(alert);
    }

    private long windowSec(Rule rule) {
        return rule.getTimeWindowSec() != null ? rule.getTimeWindowSec() : 60;
    }

    /** 构建 Alert 对象（与 SQL 规则引擎保持一致） */
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

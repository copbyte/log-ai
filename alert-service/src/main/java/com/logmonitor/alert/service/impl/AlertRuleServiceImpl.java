package com.logmonitor.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.enums.MatchType;
import com.logmonitor.alert.mapper.AlertRuleMapper;
import com.logmonitor.alert.service.AlertRuleService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements AlertRuleService {

    private volatile List<AlertRule> enabledRulesCache = Collections.emptyList();

    @Override
    public List<AlertRule> listEnabled() {
        List<AlertRule> cache = enabledRulesCache;
        if (cache.isEmpty()) {
            refreshCache();
            cache = enabledRulesCache;
        }
        return cache;
    }

    @Override
    public void refreshCache() {
        enabledRulesCache = list(new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getIsEnabled, true));
    }

    @Scheduled(fixedDelay = 30_000)
    public void scheduledRefresh() {
        refreshCache();
    }

    @Override
    public boolean matchRule(AlertRule rule, LogEntry logEntry) {
        if (rule.getLogLevel() != null && !rule.getLogLevel().isBlank()) {
            if (!rule.getLogLevel().equalsIgnoreCase(logEntry.getLogLevel())) {
                return false;
            }
        }

        String keyword = rule.getKeyword();
        String content = logEntry.getContent();
        if (content == null) {
            return false;
        }

        if (rule.getMatchType() == MatchType.REGEX) {
            return Pattern.compile(keyword, Pattern.CASE_INSENSITIVE).matcher(content).find();
        }

        return content.toLowerCase().contains(keyword.toLowerCase());
    }
}

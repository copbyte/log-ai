package com.logmonitor.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.enums.MatchType;
import com.logmonitor.alert.mapper.AlertRuleMapper;
import com.logmonitor.alert.service.AlertRuleService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class AlertRuleServiceImpl extends ServiceImpl<AlertRuleMapper, AlertRule> implements AlertRuleService {

    @Override
    public List<AlertRule> listEnabled() {
        return list(new LambdaQueryWrapper<AlertRule>().eq(AlertRule::getIsEnabled, true));
    }

    @Override
    public boolean matchRule(AlertRule rule, LogEntry logEntry) {
        // Match log level if specified
        if (rule.getLogLevel() != null && !rule.getLogLevel().isBlank()) {
            if (!rule.getLogLevel().equalsIgnoreCase(logEntry.getLogLevel())) {
                return false;
            }
        }

        // Match keyword by type
        String keyword = rule.getKeyword();
        String content = logEntry.getContent();
        if (content == null) {
            return false;
        }

        if (rule.getMatchType() == MatchType.REGEX) {
            return Pattern.compile(keyword, Pattern.CASE_INSENSITIVE).matcher(content).find();
        }

        // Default: CONTAINS (case-insensitive)
        return content.toLowerCase().contains(keyword.toLowerCase());
    }
}

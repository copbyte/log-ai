package com.logmonitor.alert.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.common.entity.LogEntry;

import java.util.List;

public interface AlertRuleService extends IService<AlertRule> {

    List<AlertRule> listEnabled();

    boolean matchRule(AlertRule rule, LogEntry logEntry);
}

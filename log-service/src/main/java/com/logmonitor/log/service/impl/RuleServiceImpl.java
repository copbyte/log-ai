package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.Rule;
import com.logmonitor.log.mapper.RuleMapper;
import com.logmonitor.log.service.RuleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 安全规则服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuleServiceImpl extends ServiceImpl<RuleMapper, Rule> implements RuleService {

    @Override
    public List<Rule> listRules() {
        return list();
    }

    @Override
    public Rule getRule(Long id) {
        return getById(id);
    }

    @Override
    public Rule createRule(Rule rule) {
        save(rule);
        return rule;
    }

    @Override
    public Rule updateRule(Rule rule) {
        updateById(rule);
        return rule;
    }

    @Override
    public boolean deleteRule(Long id) {
        return removeById(id);
    }

    @Override
    public boolean toggleRule(Long id, Boolean enabled) {
        Rule rule = getById(id);
        if (rule == null) {
            return false;
        }
        rule.setEnabled(enabled);
        return updateById(rule);
    }
}

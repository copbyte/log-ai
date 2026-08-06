package com.logmonitor.log.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.logmonitor.common.entity.Rule;

import java.util.List;

/**
 * 安全规则服务
 */
public interface RuleService extends IService<Rule> {

    /** 查询所有规则 */
    List<Rule> listRules();

    /** 查询单条规则 */
    Rule getRule(Long id);

    /** 创建规则 */
    Rule createRule(Rule rule);

    /** 更新规则 */
    Rule updateRule(Rule rule);

    /** 删除规则 */
    boolean deleteRule(Long id);

    /** 启用/禁用规则 */
    boolean toggleRule(Long id, Boolean enabled);
}

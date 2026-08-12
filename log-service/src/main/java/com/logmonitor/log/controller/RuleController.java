package com.logmonitor.log.controller;

import com.logmonitor.common.entity.Rule;
import com.logmonitor.common.result.Result;
import com.logmonitor.log.audit.AuditLog;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.service.RuleService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 安全规则 REST API
 */
@RestController
@RequestMapping("/api/rule")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    /** 查询所有规则 */
    @GetMapping
    @RateLimit(limit = 30, windowSeconds = 10, key = "rule:list")
    public Result<List<Rule>> list() {
        return Result.success(ruleService.listRules());
    }

    /** 创建规则 */
    @PostMapping
    @AuditLog(operation = "RULE_CREATE")
    public Result<Rule> create(@RequestBody Rule rule) {
        return Result.success(ruleService.createRule(rule));
    }

    /** 更新规则 */
    @PutMapping("/{id}")
    @AuditLog(operation = "RULE_UPDATE")
    public Result<Rule> update(@PathVariable("id") Long id, @RequestBody Rule rule) {
        rule.setId(id);
        return Result.success(ruleService.updateRule(rule));
    }

    /** 删除规则 */
    @DeleteMapping("/{id}")
    @AuditLog(operation = "RULE_DELETE")
    public Result<Void> delete(@PathVariable("id") Long id) {
        ruleService.deleteRule(id);
        return Result.success();
    }

    /** 启用/禁用规则 */
    @PutMapping("/{id}/toggle")
    @AuditLog(operation = "RULE_TOGGLE")
    public Result<Void> toggle(@PathVariable("id") Long id,
                               @RequestParam("enabled") Boolean enabled) {
        ruleService.toggleRule(id, enabled);
        return Result.success();
    }
}

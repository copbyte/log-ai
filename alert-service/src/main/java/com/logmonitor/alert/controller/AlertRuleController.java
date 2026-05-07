package com.logmonitor.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AlertRule;
import com.logmonitor.common.result.Result;
import com.logmonitor.alert.service.AlertRuleService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert")
public class AlertRuleController {

    private final AlertRuleService alertRuleService;

    public AlertRuleController(AlertRuleService alertRuleService) {
        this.alertRuleService = alertRuleService;
    }

    @GetMapping("/rules")
    public Result<IPage<AlertRule>> page(@RequestParam(defaultValue = "1") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return Result.success(alertRuleService.page(new Page<>(page, size)));
    }

    @GetMapping("/rules/{id}")
    public Result<AlertRule> getById(@PathVariable Long id) {
        return Result.success(alertRuleService.getById(id));
    }

    @PostMapping("/rules")
    public Result<AlertRule> create(@Valid @RequestBody AlertRule rule) {
        alertRuleService.save(rule);
        return Result.success(rule);
    }

    @PutMapping("/rules/{id}")
    public Result<AlertRule> update(@PathVariable Long id, @Valid @RequestBody AlertRule rule) {
        rule.setId(id);
        alertRuleService.updateById(rule);
        return Result.success(rule);
    }

    @DeleteMapping("/rules/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        alertRuleService.removeById(id);
        return Result.success();
    }
}

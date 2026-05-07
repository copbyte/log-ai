package com.logmonitor.alert.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.common.result.Result;
import com.logmonitor.alert.service.AlertRecordService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/alert")
public class AlertRecordController {

    private final AlertRecordService alertRecordService;

    public AlertRecordController(AlertRecordService alertRecordService) {
        this.alertRecordService = alertRecordService;
    }

    @GetMapping("/records")
    public Result<IPage<AlertRecord>> page(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return Result.success(alertRecordService.page(new Page<>(page, size)));
    }

    @GetMapping("/records/{id}")
    public Result<AlertRecord> getById(@PathVariable Long id) {
        return Result.success(alertRecordService.getById(id));
    }
}

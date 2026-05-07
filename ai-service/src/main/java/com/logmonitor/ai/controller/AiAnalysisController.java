package com.logmonitor.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AiAnalysisResult;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.ai.service.AiAnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;

    public AiAnalysisController(AiAnalysisService aiAnalysisService) {
        this.aiAnalysisService = aiAnalysisService;
    }

    @GetMapping("/results")
    public Result<IPage<AiAnalysisResult>> page(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return Result.success(aiAnalysisService.page(new Page<>(page, size)));
    }

    @GetMapping("/results/{id}")
    public Result<AiAnalysisResult> getById(@PathVariable Long id) {
        return Result.success(aiAnalysisService.getById(id));
    }

    @PostMapping("/analyze/{logEntryId}")
    public Result<AiAnalysisResult> analyze(@PathVariable Long logEntryId) {
        LogEntry logEntry = new LogEntry();
        logEntry.setId(logEntryId);
        AiAnalysisResult result = aiAnalysisService.analyze(logEntry);
        return Result.success(result);
    }
}

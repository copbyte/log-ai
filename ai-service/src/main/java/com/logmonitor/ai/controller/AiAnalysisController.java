package com.logmonitor.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AiAnalysisResult;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.common.result.Result;
import com.logmonitor.ai.mapper.LogEntryMapper;
import com.logmonitor.ai.service.AiAnalysisService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ai")
public class AiAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final LogEntryMapper logEntryMapper;

    public AiAnalysisController(AiAnalysisService aiAnalysisService,
                                 LogEntryMapper logEntryMapper) {
        this.aiAnalysisService = aiAnalysisService;
        this.logEntryMapper = logEntryMapper;
    }

    @GetMapping("/results")
    public Result<IPage<AiAnalysisResult>> page(@RequestParam(name = "page", defaultValue = "1") int page,
                                                 @RequestParam(name = "size", defaultValue = "20") int size) {
        return Result.success(aiAnalysisService.page(new Page<>(page, size)));
    }

    @GetMapping("/results/{id}")
    public Result<AiAnalysisResult> getById(@PathVariable("id") Long id) {
        return Result.success(aiAnalysisService.getById(id));
    }

    @PostMapping("/analyze/{logEntryId}")
    public Result<AiAnalysisResult> analyze(@PathVariable("logEntryId") Long logEntryId) {
        LogEntry logEntry = logEntryMapper.selectById(logEntryId);
        if (logEntry == null) {
            return Result.fail("日志条目不存在");
        }
        AiAnalysisResult result = aiAnalysisService.analyze(logEntry, true);
        return Result.success(result);
    }
}

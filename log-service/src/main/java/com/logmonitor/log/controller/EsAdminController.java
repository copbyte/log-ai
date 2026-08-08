package com.logmonitor.log.controller;

import com.logmonitor.common.result.Result;
import com.logmonitor.log.ratelimit.RateLimit;
import com.logmonitor.log.storage.EsBackfillService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ES 管理接口：存量数据回填（生产迁移用）。
 */
@RestController
@RequestMapping("/api/admin/es")
public class EsAdminController {

    private final EsBackfillService esBackfillService;

    public EsAdminController(EsBackfillService esBackfillService) {
        this.esBackfillService = esBackfillService;
    }

    /**
     * 把 MySQL 全量日志回填到 ES（幂等，可重复执行）
     * 调用：POST /api/admin/es/backfill?pageSize=500（需登录）
     */
    @PostMapping("/backfill")
    @RateLimit(limit = 5, windowSeconds = 60, key = "es:backfill")
    public Result<Map<String, Object>> backfill(
            @RequestParam(name = "pageSize", defaultValue = "500") int pageSize) {
        long total = esBackfillService.backfill(pageSize);
        return Result.success(Map.of("indexed", total));
    }
}

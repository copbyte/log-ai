package com.logmonitor.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.logmonitor.common.entity.LogEntry;

import java.time.LocalDateTime;
import java.util.List;

public interface LogEntryService extends IService<LogEntry> {

    /**
     * 分页查询日志（支持多维度筛选）
     */
    IPage<LogEntry> pageWithFilters(Page<LogEntry> page,
                                     String logLevel,
                                     String className,
                                     String fileName,
                                     String threadName,
                                     LocalDateTime startTime,
                                     LocalDateTime endTime,
                                     String keyword,
                                     String traceId,
                                     String serviceName,
                                     String logSource,
                                     String srcIp,
                                     String action);

    /**
     * 按 TraceID 查询关联日志（按时间升序）
     */
    List<LogEntry> getLogsByTraceId(String traceId);
}

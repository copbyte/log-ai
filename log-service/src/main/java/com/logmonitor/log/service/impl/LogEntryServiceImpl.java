package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.security.SensitiveDataMasker;
import com.logmonitor.log.service.LogEntryService;
import com.logmonitor.log.storage.EsLogSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LogEntryServiceImpl extends ServiceImpl<LogEntryMapper, LogEntry> implements LogEntryService {

    /** 日志内容敏感信息脱敏开关（password/token/手机号/身份证） */
    @Value("${app.security.mask-sensitive:true}")
    private boolean maskSensitive;

    private final EsLogSearchService esLogSearchService;

    public LogEntryServiceImpl(EsLogSearchService esLogSearchService) {
        this.esLogSearchService = esLogSearchService;
    }

    @Override
    public IPage<LogEntry> pageWithFilters(Page<LogEntry> page,
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
                                           String action) {
        // ES 优先：全文检索/多维筛选在 ES 执行，命中率与时延优于 MySQL LIKE
        IPage<LogEntry> result = esLogSearchService.search(page, logLevel, className, fileName, threadName,
                startTime, endTime, keyword, traceId, serviceName, logSource, srcIp, action);
        if (result == null || result.getTotal() == 0) {
            // ES 关闭/异常/空结果都回退 MySQL：
            // 双写启用前的存量数据可能尚未回填到 ES，保证演示与迁移期查询结果一致
            result = pageFromMysql(page, logLevel, className, fileName, threadName,
                    startTime, endTime, keyword, traceId, serviceName, logSource, srcIp, action);
        }
        if (maskSensitive) {
            result.getRecords().forEach(e -> e.setContent(SensitiveDataMasker.mask(e.getContent())));
        }
        return result;
    }

    /** MySQL 分页查询（ES 不可用时的降级路径） */
    private IPage<LogEntry> pageFromMysql(Page<LogEntry> page,
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
                                          String action) {
        LambdaQueryWrapper<LogEntry> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(logLevel)) {
            wrapper.eq(LogEntry::getLogLevel, logLevel.toUpperCase());
        }
        if (StringUtils.hasText(srcIp)) {
            wrapper.eq(LogEntry::getSrcIp, srcIp);
        }
        if (StringUtils.hasText(action)) {
            wrapper.eq(LogEntry::getAction, action);
        }
        if (StringUtils.hasText(className)) {
            wrapper.like(LogEntry::getClassName, className);
        }
        if (StringUtils.hasText(fileName)) {
            wrapper.like(LogEntry::getFileName, fileName);
        }
        if (StringUtils.hasText(threadName)) {
            wrapper.like(LogEntry::getThreadName, threadName);
        }
        if (startTime != null) {
            wrapper.ge(LogEntry::getLogTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(LogEntry::getLogTime, endTime);
        }
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(LogEntry::getContent, keyword)
                    .or()
                    .like(LogEntry::getClassName, keyword)
                    .or()
                    .like(LogEntry::getFileName, keyword));
        }
        if (StringUtils.hasText(traceId)) {
            wrapper.eq(LogEntry::getTraceId, traceId);
        }
        if (StringUtils.hasText(serviceName)) {
            wrapper.eq(LogEntry::getServiceName, serviceName);
        }
        if (StringUtils.hasText(logSource)) {
            wrapper.eq(LogEntry::getLogSource, logSource);
        }

        wrapper.orderByDesc(LogEntry::getId);
        return baseMapper.selectPage(page, wrapper);
    }

    @Override
    public List<LogEntry> getLogsByTraceId(String traceId) {
        // ES 优先：TraceID 精确匹配 + 时间升序
        List<LogEntry> logs = esLogSearchService.findByTraceId(traceId);
        if (logs == null || logs.isEmpty()) {
            // ES 空结果同样回查 MySQL（存量数据可能未回填）
            logs = logsFromMysql(traceId);
        }
        if (maskSensitive) {
            logs.forEach(e -> e.setContent(SensitiveDataMasker.mask(e.getContent())));
        }
        return logs;
    }

    /** MySQL 链路查询（ES 不可用时的降级路径） */
    private List<LogEntry> logsFromMysql(String traceId) {
        LambdaQueryWrapper<LogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogEntry::getTraceId, traceId)
               .orderByAsc(LogEntry::getLogTime);
        return list(wrapper);
    }
}

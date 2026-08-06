package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.service.LogEntryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LogEntryServiceImpl extends ServiceImpl<LogEntryMapper, LogEntry> implements LogEntryService {

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
        LambdaQueryWrapper<LogEntry> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LogEntry::getTraceId, traceId)
               .orderByAsc(LogEntry::getLogTime);
        return list(wrapper);
    }
}

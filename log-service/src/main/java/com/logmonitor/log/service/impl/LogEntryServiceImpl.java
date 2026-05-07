package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.service.LogEntryService;
import org.springframework.stereotype.Service;

@Service
public class LogEntryServiceImpl extends ServiceImpl<LogEntryMapper, LogEntry> implements LogEntryService {
}

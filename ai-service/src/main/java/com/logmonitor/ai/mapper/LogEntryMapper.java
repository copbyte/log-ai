package com.logmonitor.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.LogEntry;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LogEntryMapper extends BaseMapper<LogEntry> {
}

package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}

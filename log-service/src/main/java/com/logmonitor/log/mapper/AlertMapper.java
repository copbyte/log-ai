package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.Alert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全告警 Mapper
 */
@Mapper
public interface AlertMapper extends BaseMapper<Alert> {
}

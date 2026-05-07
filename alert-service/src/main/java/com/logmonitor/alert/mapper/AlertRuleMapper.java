package com.logmonitor.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.AlertRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertRuleMapper extends BaseMapper<AlertRule> {
}

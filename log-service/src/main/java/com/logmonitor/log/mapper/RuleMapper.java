package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.Rule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 安全规则 Mapper
 */
@Mapper
public interface RuleMapper extends BaseMapper<Rule> {
}

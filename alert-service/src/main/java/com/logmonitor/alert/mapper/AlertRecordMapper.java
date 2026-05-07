package com.logmonitor.alert.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.AlertRecord;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AlertRecordMapper extends BaseMapper<AlertRecord> {
}

package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.DlxMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 死信消息Mapper，继承MyBatis-Plus BaseMapper获得标准CRUD方法
 */
@Mapper
public interface DlxMessageMapper extends BaseMapper<DlxMessage> {
}

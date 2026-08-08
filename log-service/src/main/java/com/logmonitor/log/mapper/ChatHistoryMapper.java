package com.logmonitor.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.logmonitor.common.entity.ChatHistory;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChatHistoryMapper extends BaseMapper<ChatHistory> {
}

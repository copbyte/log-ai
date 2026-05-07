package com.logmonitor.alert.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.alert.mapper.AlertRecordMapper;
import com.logmonitor.alert.service.AlertRecordService;
import org.springframework.stereotype.Service;

@Service
public class AlertRecordServiceImpl extends ServiceImpl<AlertRecordMapper, AlertRecord> implements AlertRecordService {
}

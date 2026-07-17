package com.logmonitor.alert.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.AlertRecord;
import com.logmonitor.alert.mapper.AlertRecordMapper;
import com.logmonitor.alert.service.AlertRecordService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class AlertRecordServiceImpl extends ServiceImpl<AlertRecordMapper, AlertRecord> implements AlertRecordService {

    public IPage<AlertRecord> pageWithFilters(Page<AlertRecord> page,
                                               Long ruleId,
                                               String notifyStatus,
                                               LocalDateTime startTime,
                                               LocalDateTime endTime) {
        LambdaQueryWrapper<AlertRecord> wrapper = new LambdaQueryWrapper<>();

        if (ruleId != null) {
            wrapper.eq(AlertRecord::getRuleId, ruleId);
        }
        if (StringUtils.hasText(notifyStatus)) {
            wrapper.eq(AlertRecord::getNotifyStatus, notifyStatus.toUpperCase());
        }
        if (startTime != null) {
            wrapper.ge(AlertRecord::getCreateTime, startTime);
        }
        if (endTime != null) {
            wrapper.le(AlertRecord::getCreateTime, endTime);
        }

        wrapper.orderByDesc(AlertRecord::getId);
        return baseMapper.selectPage(page, wrapper);
    }
}

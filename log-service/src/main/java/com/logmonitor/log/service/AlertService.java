package com.logmonitor.log.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.logmonitor.common.entity.Alert;

import java.util.Map;

/**
 * 安全告警服务
 */
public interface AlertService extends IService<Alert> {

    /** 分页查询告警，支持按状态和严重级别筛选 */
    IPage<Alert> listAlerts(String status, Integer severity, int page, int size);

    /** 查询单条告警 */
    Alert getAlert(Long id);

    /** 确认告警（状态改为 ACK） */
    boolean ackAlert(Long id);

    /** 解决告警（状态改为 RESOLVED） */
    boolean resolveAlert(Long id);

    /** 告警统计（总数、各状态数、各严重级别数、最近24小时趋势） */
    Map<String, Object> getAlertStats();
}

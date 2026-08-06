package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.logmonitor.common.entity.Alert;
import com.logmonitor.log.mapper.AlertMapper;
import com.logmonitor.log.service.AlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 安全告警服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AlertServiceImpl extends ServiceImpl<AlertMapper, Alert> implements AlertService {

    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    @Override
    public IPage<Alert> listAlerts(String status, Integer severity, int page, int size) {
        LambdaQueryWrapper<Alert> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(status)) {
            wrapper.eq(Alert::getStatus, status);
        }
        if (severity != null) {
            wrapper.eq(Alert::getSeverity, severity);
        }
        wrapper.orderByDesc(Alert::getId);
        return page(new Page<>(page, size), wrapper);
    }

    @Override
    public Alert getAlert(Long id) {
        return getById(id);
    }

    @Override
    public boolean ackAlert(Long id) {
        Alert alert = getById(id);
        if (alert == null) {
            return false;
        }
        alert.setStatus("ACK");
        return updateById(alert);
    }

    @Override
    public boolean resolveAlert(Long id) {
        Alert alert = getById(id);
        if (alert == null) {
            return false;
        }
        alert.setStatus("RESOLVED");
        return updateById(alert);
    }

    @Override
    public Map<String, Object> getAlertStats() {
        Map<String, Object> stats = new HashMap<>();

        // 总数
        stats.put("total", count());

        // 各状态数
        Map<String, Long> statusCount = new HashMap<>();
        for (String status : new String[]{"OPEN", "ACK", "RESOLVED"}) {
            statusCount.put(status, count(new LambdaQueryWrapper<Alert>().eq(Alert::getStatus, status)));
        }
        stats.put("statusCount", statusCount);

        // 各严重级别数（查询全部告警后 Java 端分组）
        Map<Integer, Long> severityCount = new HashMap<>();
        List<Alert> allAlerts = list();
        for (Alert alert : allAlerts) {
            if (alert.getSeverity() != null) {
                severityCount.merge(alert.getSeverity(), 1L, Long::sum);
            }
        }
        stats.put("severityCount", severityCount);

        // 最近24小时趋势（按小时分组）
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<Alert> recentAlerts = list(new LambdaQueryWrapper<Alert>().ge(Alert::getCreateTime, since));
        Map<String, Long> hourlyTrend = new HashMap<>();
        for (Alert alert : recentAlerts) {
            if (alert.getCreateTime() != null) {
                String hourKey = alert.getCreateTime().format(HOUR_FMT);
                hourlyTrend.merge(hourKey, 1L, Long::sum);
            }
        }
        stats.put("hourlyTrend", hourlyTrend);
        stats.put("recent24hCount", (long) recentAlerts.size());

        return stats;
    }
}

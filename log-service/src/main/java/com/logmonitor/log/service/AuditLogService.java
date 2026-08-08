package com.logmonitor.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AuditLog;
import com.logmonitor.log.mapper.AuditLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 审计日志服务：记录关键操作，支持分页查询。
 * <p>
 * 写入采用同步 + 失败静默（fail-safe），审计失败不影响业务；
 * 高并发生产环境可改为异步线程池或 MQ 投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /** 记录一条审计日志（失败不影响业务） */
    public void record(String operation, String username, String params, String result, String errorMessage, String ip) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .operation(operation)
                    .username(username)
                    .params(params)
                    .result(result)
                    .errorMessage(errorMessage)
                    .ip(ip)
                    .build();
            auditLogMapper.insert(auditLog);
        } catch (Exception e) {
            log.error("审计日志写入失败: {}", e.getMessage());
        }
    }

    /** 分页查询审计日志（可按操作类型/用户筛选） */
    public IPage<AuditLog> page(int page, int size, String operation, String username) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(operation)) {
            wrapper.eq(AuditLog::getOperation, operation);
        }
        if (StringUtils.hasText(username)) {
            wrapper.eq(AuditLog::getUsername, username);
        }
        wrapper.orderByDesc(AuditLog::getId);
        return auditLogMapper.selectPage(new Page<>(page, size), wrapper);
    }
}

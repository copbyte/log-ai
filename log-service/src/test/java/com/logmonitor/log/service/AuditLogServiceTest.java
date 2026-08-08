package com.logmonitor.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.AuditLog;
import com.logmonitor.log.mapper.AuditLogMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计服务测试：写入审计、写入失败不抛异常、分页查询。
 */
class AuditLogServiceTest {

    @Test
    void recordInsertsAuditLog() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        AuditLogService service = new AuditLogService(mapper);

        service.record("LOGIN", "admin", "params", "SUCCESS", null, "127.0.0.1");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(mapper).insert((AuditLog) captor.capture());
        assertEquals("LOGIN", captor.getValue().getOperation());
        assertEquals("admin", captor.getValue().getUsername());
        assertEquals("SUCCESS", captor.getValue().getResult());
    }

    @Test
    void recordFailureDoesNotThrow() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        doThrow(new RuntimeException("db down")).when(mapper).insert((AuditLog) any());
        AuditLogService service = new AuditLogService(mapper);

        assertDoesNotThrow(() -> service.record("LOGIN", "admin", null, "SUCCESS", null, null));
    }

    @Test
    void pageDelegatesToMapper() {
        AuditLogMapper mapper = mock(AuditLogMapper.class);
        when(mapper.selectPage(any(), any())).thenReturn(new Page<>());
        AuditLogService service = new AuditLogService(mapper);

        service.page(1, 10, "LOGIN", "admin");

        verify(mapper).selectPage(any(), any());
    }
}

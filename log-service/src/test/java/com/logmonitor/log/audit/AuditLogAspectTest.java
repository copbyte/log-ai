package com.logmonitor.log.audit;

import com.logmonitor.log.service.AuditLogService;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 审计切面测试：成功记录 SUCCESS、失败记录 FAIL 并重抛。
 */
class AuditLogAspectTest {

    private AuditLogService auditLogService;
    private AuditLogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        auditLogService = mock(AuditLogService.class);
        aspect = new AuditLogAspect(auditLogService);
        Field enabled = AuditLogAspect.class.getDeclaredField("enabled");
        enabled.setAccessible(true);
        enabled.set(aspect, true);
    }

    @Test
    void recordsSuccessAndProceeds() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenReturn("ok");

        Object result = aspect.around(pjp, TestOps.class.getMethod("success").getAnnotation(AuditLog.class));

        assertEquals("ok", result);
        verify(auditLogService).record(eq("TEST_OP"), isNull(), anyString(), eq("SUCCESS"), isNull(), isNull());
    }

    @Test
    void recordsFailureAndRethrows() throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.proceed()).thenThrow(new RuntimeException("boom"));

        assertThrows(RuntimeException.class, () -> aspect.around(
                pjp, TestOps.class.getMethod("failure").getAnnotation(AuditLog.class)));

        verify(auditLogService).record(eq("TEST_OP"), isNull(), anyString(), eq("FAIL"), eq("boom"), isNull());
    }

    static class TestOps {
        @AuditLog(operation = "TEST_OP")
        public void success() {
        }

        @AuditLog(operation = "TEST_OP")
        public void failure() {
        }
    }
}

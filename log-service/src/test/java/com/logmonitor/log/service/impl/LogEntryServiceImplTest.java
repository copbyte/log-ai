package com.logmonitor.log.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import com.logmonitor.log.storage.EsLogSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 日志查询回退测试：ES 返回空结果时必须回查 MySQL，保证存量数据可见。
 */
class LogEntryServiceImplTest {

    private EsLogSearchService esSearch;
    private LogEntryMapper mapper;
    private LogEntryServiceImpl service;

    @BeforeEach
    void setUp() {
        esSearch = mock(EsLogSearchService.class);
        mapper = mock(LogEntryMapper.class);
        service = new LogEntryServiceImpl(esSearch);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
    }

    @Test
    void fallsBackToMysqlWhenEsReturnsEmpty() {
        Page<LogEntry> emptyEs = new Page<>(1, 20, 0);
        emptyEs.setRecords(List.of());
        when(esSearch.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(emptyEs);

        Page<LogEntry> mysqlPage = new Page<>(1, 20, 1);
        LogEntry row = LogEntry.builder().traceId("trace-001").content("mock").build();
        mysqlPage.setRecords(List.of(row));
        when(mapper.selectPage(any(), any())).thenReturn(mysqlPage);

        Page<LogEntry> result = (Page<LogEntry>) service.pageWithFilters(
                new Page<>(1, 20), null, null, null, null,
                null, null, null, "trace-001", null, null, null, null);

        assertEquals(1, result.getTotal());
        assertEquals("trace-001", result.getRecords().get(0).getTraceId());
        verify(mapper).selectPage(any(), any());
    }

    @Test
    void keepsEsResultWhenEsHasData() {
        Page<LogEntry> esPage = new Page<>(1, 20, 1);
        esPage.setRecords(List.of(LogEntry.builder().traceId("trace-001").content("es").build()));
        when(esSearch.search(any(), any(), any(), any(), any(), any(), any(), any(),
                any(), any(), any(), any(), any())).thenReturn(esPage);

        service.pageWithFilters(new Page<>(1, 20), null, null, null, null,
                null, null, null, "trace-001", null, null, null, null);

        verify(mapper, never()).selectPage(any(), any());
    }
}

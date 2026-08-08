package com.logmonitor.log.storage;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.LogEntry;
import com.logmonitor.log.mapper.LogEntryMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ES 存量回填测试：分页读取 MySQL 并写入 ES，末页不足一页时停止。
 */
class EsBackfillServiceTest {

    @Test
    void backfillWritesAllPages() {
        LogEntryMapper mapper = mock(LogEntryMapper.class);
        EsLogWriter writer = mock(EsLogWriter.class);
        Page<LogEntry> fullPage = new Page<>(1, 2, 3);
        fullPage.setRecords(List.of(entry(1), entry(2)));
        Page<LogEntry> lastPage = new Page<>(2, 2, 3);
        lastPage.setRecords(List.of(entry(3)));
        when(mapper.selectPage(any(), any())).thenReturn(fullPage, lastPage);
        EsBackfillService service = new EsBackfillService(mapper, writer);

        long total = service.backfill(2);

        assertEquals(3, total);
        verify(writer, times(2)).write(any());
    }

    @Test
    void backfillStopsWhenEmpty() {
        LogEntryMapper mapper = mock(LogEntryMapper.class);
        EsLogWriter writer = mock(EsLogWriter.class);
        when(mapper.selectPage(any(), any())).thenReturn(new Page<>());
        EsBackfillService service = new EsBackfillService(mapper, writer);

        assertEquals(0, service.backfill(2));
        verify(writer, times(0)).write(any());
    }

    private static LogEntry entry(long id) {
        LogEntry e = LogEntry.builder().content("log-" + id).build();
        e.setId(id);
        return e;
    }
}

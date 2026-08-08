package com.logmonitor.log.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * H2 偏移量存储集成测试：验证 upsert、批量 flush 与删除清理。
 * <p>
 * 注意：H2FileOffsetStore 内部自建 H2 连接（不注册 DataSource Bean），
 * 测试通过反射注入内存库地址。
 */
class H2FileOffsetStoreTest {

    private H2FileOffsetStore store;

    @BeforeEach
    void setUp() throws Exception {
        store = new H2FileOffsetStore();
        Field url = H2FileOffsetStore.class.getDeclaredField("h2Url");
        url.setAccessible(true);
        url.set(store, "jdbc:h2:mem:offset_test;MODE=MySQL;DB_CLOSE_DELAY=-1");
        store.init();
    }

    @Test
    void saveOffsetThenGetReturnsValue() {
        store.saveOffset("/logs/app.log", 1024L);
        assertEquals(1024L, store.getOffset("/logs/app.log"));
    }

    @Test
    void getOffsetForUnknownFileReturnsZero() {
        assertEquals(0L, store.getOffset("/logs/not-exist.log"));
    }

    @Test
    void flushBatchUpsertsThenDeleteRemoves() {
        Map<String, Long> offsets = new HashMap<>();
        offsets.put("/logs/a.log", 10L);
        offsets.put("/logs/b.log", 20L);
        store.flush(offsets);
        assertEquals(10L, store.getOffset("/logs/a.log"));
        assertEquals(20L, store.getOffset("/logs/b.log"));

        store.delete("/logs/a.log");
        assertEquals(0L, store.getOffset("/logs/a.log"));
        assertEquals(20L, store.getOffset("/logs/b.log"));
    }
}

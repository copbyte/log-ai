package com.logmonitor.log.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.ChatHistory;
import com.logmonitor.log.mapper.ChatHistoryMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 对话历史服务测试：按登录用户写入、非法消息跳过、分页查询。
 */
class ChatHistoryServiceTest {

    @Test
    void saveBatchSetsUsernameFromLoginAndWritesBothMessages() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryService service = new ChatHistoryService(mapper);

        service.saveBatch("admin", List.of(
                ChatHistory.builder().role("user").content("搜索ERROR日志").build(),
                ChatHistory.builder().role("assistant").content("共找到10条").build()));

        ArgumentCaptor<ChatHistory> captor = ArgumentCaptor.forClass(ChatHistory.class);
        verify(mapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertEquals("admin", captor.getAllValues().get(0).getUsername());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
    }

    @Test
    void saveBatchSkipsBlankAndInvalidRole() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryService service = new ChatHistoryService(mapper);

        service.saveBatch("admin", List.of(
                ChatHistory.builder().role("user").content("").build(),
                ChatHistory.builder().role("robot").content("not valid").build(),
                ChatHistory.builder().role("assistant").content("ok").build()));

        verify(mapper, org.mockito.Mockito.times(1)).insert(any(ChatHistory.class));
    }

    @Test
    void saveBatchWithoutLoginDoesNothing() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        ChatHistoryService service = new ChatHistoryService(mapper);

        service.saveBatch("", List.of(ChatHistory.builder().role("user").content("x").build()));

        verify(mapper, never()).insert(any(ChatHistory.class));
    }

    @Test
    void pageDelegatesToMapper() {
        ChatHistoryMapper mapper = mock(ChatHistoryMapper.class);
        when(mapper.selectPage(any(), any())).thenReturn(new Page<>());
        ChatHistoryService service = new ChatHistoryService(mapper);

        assertDoesNotThrow(() -> service.page("admin", 1, 20));
        verify(mapper).selectPage(any(), any());
    }
}

package com.logmonitor.mcp.controller;

import com.logmonitor.mcp.client.LogServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

/**
 * 对话输入校验测试：空消息与超过 1000 字的消息必须被拒绝。
 */
class ChatControllerTest {

    private final ChatController controller =
            new ChatController(mock(ChatClient.class), mock(LogServiceClient.class));

    @Test
    void blankMessageIsRejected() {
        assertThrows(ResponseStatusException.class, () ->
                controller.chat(new ChatController.ChatRequest("   ", List.of()), null));
    }

    @Test
    void tooLongMessageIsRejected() {
        String message = "a".repeat(1001);
        assertThrows(ResponseStatusException.class, () ->
                controller.chat(new ChatController.ChatRequest(message, List.of()), null));
    }

    @Test
    void blankStreamMessageIsRejected() {
        assertThrows(ResponseStatusException.class, () ->
                controller.stream(new ChatController.ChatRequest("", List.of()), null));
    }
}

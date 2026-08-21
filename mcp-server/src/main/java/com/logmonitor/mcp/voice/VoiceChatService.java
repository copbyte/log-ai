package com.logmonitor.mcp.voice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

@Slf4j
@Service
public class VoiceChatService {

    //    private final ChatClient.Builder chatClientBuilder;
    private final ChatClient chatClient;

    public VoiceChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * 语音指令 → AI 回答（复用现有 ChatClient + @Tool 链路）
     */
    public String chat(String command, List<Message> history) {
        // TODO: 调用 ChatClient，跟 ChatController 的逻辑一样
        // 提示:
        // 1. 构建 messages 列表（历史 + 当前指令）
        try {
            List<Message> messages = new ArrayList<>();
            // 2. chatClientBuilder.build().prompt().messages(messages).tools(...).call().content()
            if (history != null && !history.isEmpty()) {
                int from = Math.max(0, history.size() - 20);
                messages.addAll(history.subList(from, history.size()));
            }
            messages.add(new UserMessage(command));
            // 3. 或者直接注入 ChatClient（如果已有配置）
            // 注意: 这里不做流式（语音场景流式意义不大），直接 call() 拿完整回答
            String answer = chatClient.prompt().messages(messages).call().content();
            return answer == null ? "" : answer;
        } catch (Exception e) {
            log.error("语音对话失败:{}", e.getMessage());
            return "抱歉,我暂时无法回答,请稍后再试";
        }

    }
}
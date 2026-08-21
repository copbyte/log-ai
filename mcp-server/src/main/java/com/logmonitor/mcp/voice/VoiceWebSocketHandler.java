package com.logmonitor.mcp.voice;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;
import org.vosk.Recognizer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * 语音助手 WebSocket 处理器
 * <p>
 * 协议：
 * - 前端 → 后端：BinaryMessage（PCM 音频流，16kHz 16bit 单声道）
 * - 后端 → 前端：TextMessage（JSON）
 * {"type": "status", "state": "listening|wake|processing|speaking"}
 * {"type": "partial", "text": "中间识别结果"}
 * {"type": "final", "text": "完整识别结果"}
 * {"type": "wake", "text": "唤醒成功"}
 * {"type": "response", "text": "AI 回答"}
 * {"type": "error", "text": "错误信息"}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoiceWebSocketHandler extends BinaryWebSocketHandler {

    private final VoskSpeechService speechService;
    private final VoiceChatService voiceChatService;  // 第 4 步实现

    // 每个连接的状态
    private static class SessionState {
        Recognizer recognizer;
        boolean awakened = false;// 是否已唤醒
        boolean processing = false;
        StringBuilder commandBuffer = new StringBuilder();  // 指令文本缓冲
        List<Message> history = new ArrayList<>();   // 本轮语音会话的历史
    }

    // 用 Map 存每个 WebSocket 会话的状态（线程安全）
    private final java.util.Map<WebSocketSession, SessionState> sessions =
            new java.util.concurrent.ConcurrentHashMap<>();
    // AI 回答要等几秒，用独立线程池异步执行，避免阻塞 WebSocket 音频处理线程
    private final java.util.concurrent.ExecutorService chatExecutor =
            java.util.concurrent.Executors.newFixedThreadPool(2);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // TODO 1: 创建识别器，存入 sessions
        // 提示:
        // SessionState state = new SessionState();
        // state.recognizer = speechService.createRecognizer();
        // sessions.put(session, state);
        // 发送状态: sendText(session, "status", "listening")
        SessionState state = new SessionState();
        state.recognizer = speechService.createRecognizer();
        sessions.put(session, state);
        sendText(session, "status", "listening");
        log.info("Voice WebSocket connected: {}", session.getId());
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        SessionState state = sessions.get(session);
        if (state == null) return;

        // TODO 2: 处理音频
        // 提示:
        // 1. ByteBuffer buf = message.getPayload();
        ByteBuffer buff = message.getPayload();
        // 2. byte[] audio = new byte[buf.remaining()]; buf.get(audio);
        byte[] bytes = new byte[buff.remaining()];
        buff.get(bytes);
        // 3. String text = speechService.recognize(state.recognizer, audio);
        VoskSpeechService.RecognitionResult result = speechService.recognize(state.recognizer, bytes);
        String s = result.text();
        if (s.isEmpty()) return;
        // 4. 如果 text 非空:
        //    - 未唤醒: 检查唤醒词 → 命中则 awakened=true, 发送 {"type":"wake"}
        //    - 已唤醒: 追加到 commandBuffer, 发送 {"type":"partial","text":...}
        if (!state.awakened) {
            if (speechService.isWakeWord(s)) {
                state.awakened = true;
                state.commandBuffer.setLength(0);
                sendText(session, "wake", "请说话");
            }
            return;
        }

        // 5. 如果识别到句号/静默（一句话结束）且已唤醒:
        //    - 拿 commandBuffer 完整文本
        //    - 调用 voiceChatService.chat(text) 获取 AI 回答
        //    - 发送 {"type":"response","text":AI回答}
        //    - 重置: awakened=false, commandBuffer清空
        if (result.finalResult()) {
            state.commandBuffer.append(s);
            String command = state.commandBuffer.toString().trim();
            state.commandBuffer.setLength(0);
            if (command.isEmpty()) return;
            state.processing = true;
            sendText(session, "status", "processing");
            chatExecutor.execute(() -> {
                        try {
                            String chat = voiceChatService.chat(command,state.history);
                            sendText(session, "response", chat);
                            state.history.add(new UserMessage(command));
                            state.history.add(new AssistantMessage(chat));
                        } catch (Exception e) {
                            log.error("AI回答失败:{}", e.getMessage());
                            sendText(session, "error", "处理失败" +e.getMessage());
                        } finally {
                            state.processing = false;
                            state.awakened = false;
                            sendText(session, "status", "listening");
                        }
                    }
            );
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable e) {
        log.error("Voice WebSocket error: {}", e.getMessage());
        cleanup(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        cleanup(session);
        log.info("Voice WebSocket closed: {}", session.getId());
    }

    private void cleanup(WebSocketSession session) {
        SessionState state = sessions.remove(session);
        if (state != null && state.recognizer != null) {
            state.recognizer.close();
        }
    }

    private void sendText(WebSocketSession session, String type, String text) {
        try {
            JSONObject json = new JSONObject();
            json.put("type", type);
            json.put("text", text);
            session.sendMessage(new TextMessage(json.toJSONString()));
        } catch (Exception e) {
            log.warn("Send WebSocket message failed: {}", e.getMessage());
        }
    }
}
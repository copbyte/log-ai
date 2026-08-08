package com.logmonitor.log.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.logmonitor.common.entity.ChatHistory;
import com.logmonitor.log.mapper.ChatHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * AI 对话历史服务：按用户写入/查询，数据按用户隔离。
 * <p>
 * username 一律取自登录态（AuthContext），不信任请求体里的用户字段，避免越权写入他人记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatHistoryService {

    private static final int MAX_BATCH = 50;

    private final ChatHistoryMapper chatHistoryMapper;

    /** 批量保存一段对话（用户问题 + AI 回答），任一消息无效则跳过，整批失败不影响业务 */
    @Transactional(rollbackFor = Exception.class)
    public void saveBatch(String username, List<ChatHistory> messages) {
        if (!StringUtils.hasText(username) || messages == null || messages.isEmpty()) {
            return;
        }
        List<ChatHistory> valid = messages.stream()
                .filter(m -> m != null
                        && ("user".equals(m.getRole()) || "assistant".equals(m.getRole()))
                        && StringUtils.hasText(m.getContent()))
                .limit(MAX_BATCH)
                .peek(m -> m.setUsername(username))
                .toList();
        if (!valid.isEmpty()) {
            chatHistoryMapper.insert(valid.get(0));
            if (valid.size() > 1) {
                valid.subList(1, valid.size()).forEach(chatHistoryMapper::insert);
            }
        }
    }

    /** 分页查询指定用户的历史记录（时间倒序） */
    public IPage<ChatHistory> page(String username, int page, int size) {
        LambdaQueryWrapper<ChatHistory> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.eq(ChatHistory::getUsername, username);
        }
        wrapper.orderByDesc(ChatHistory::getId);
        return chatHistoryMapper.selectPage(new Page<>(page, size), wrapper);
    }
}

package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AI 对话历史（按用户隔离，用户只能看到自己的记录）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("chat_history")
public class ChatHistory extends BaseEntity {

    /** 归属用户（由登录态决定，不信任前端传入） */
    private String username;

    /** 消息角色：user / assistant */
    private String role;

    /** 消息内容（用户问题或 AI 回答） */
    private String content;

    /** 对话历史为追加写，无更新时间列，不参与 MyBatis-Plus 字段映射 */
    @TableField(exist = false)
    private LocalDateTime updateTime;
}

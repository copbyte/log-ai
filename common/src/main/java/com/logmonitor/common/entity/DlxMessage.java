package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 死信消息实体，对应 dlx_message 表
 * <p>
 * 记录MQ消费失败后进入死信队列的消息，防止消息永久丢失。
 * 运维人员可查询此表排查消费失败的原因。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("dlx_message")
public class DlxMessage extends BaseEntity {

    /** 死信消息原始JSON内容 */
    private String originalBody;

    /** 消息来源队列名称，如 log.monitor.ai.queue */
    private String originQueue;

    /** 失败原因摘要，从异常信息中提取 */
    private String failReason;

    /** 消费者已重试次数 */
    private Integer retryCount;

    /** 处理状态：PENDING-待处理, RESOLVED-已处理, IGNORED-已忽略 */
    private String status;

    /** 关联的原始日志ID，可解析时填入 */
    private Long logEntryId;

    /** 可解析的日志内容 */
    private String logContent;
}

package com.logmonitor.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("log_entry")
public class LogEntry extends BaseEntity {

    private String fileName;
    private String logLevel;
    private LocalDateTime logTime;
    private String content;
    private String threadName;
    private String className;
    private String filePath;
    /** SkyWalking/链路追踪 TraceID */
    private String traceId;
    /** 来源服务名 */
    private String serviceName;
    /** 日志来源类型，对应 LogSource 枚举 */
    private String logSource;
    // getter和setter方法
    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }
    @TableField(exist = false)
    private LocalDateTime updateTime;
}

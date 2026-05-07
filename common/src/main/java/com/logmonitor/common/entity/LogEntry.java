package com.logmonitor.common.entity;

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
}

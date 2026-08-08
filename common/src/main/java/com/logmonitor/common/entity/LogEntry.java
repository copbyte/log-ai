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
    /** 日志来源类型，对应 LogSource 枚举：FILE/SYSLOG/CEF/HTTP/ELK/LOKI/MOCK */
    private String logSource;
    // ===== 安全日志字段（态势感知扩展）=====
    /** 源 IP（攻击源/访问源）*/
    private String srcIp;
    /** 目的 IP（被访问目标）*/
    private String dstIp;
    /** 源端口 */
    private Integer srcPort;
    /** 目的端口 */
    private Integer dstPort;
    /** 网络协议：TCP/UDP/ICMP/HTTP/HTTPS */
    private String protocol;
    /** 防火墙/设备动作：allow/deny/blocked/drop */
    private String action;
    /** 安全事件严重级别 0-10（0=info, 10=critical）*/
    private Integer severity;
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

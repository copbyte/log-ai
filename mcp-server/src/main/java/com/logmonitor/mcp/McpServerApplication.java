package com.logmonitor.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

/**
 * MCP Server 启动类
 * 同时作为 MCP Server（供外部 MCP Client 调用）和 Chat 服务（供前端对话）
 * 排除 DataSourceAutoConfiguration：mcp-server 不直接连数据库，通过 RestClient 调用 log-service
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}

package com.logmonitor.mcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * MCP Server 启动类
 * 同时作为 MCP Server（供外部 MCP Client 调用）和 Chat 服务（供前端对话）
 */
@SpringBootApplication
public class McpServerApplication {
    public static void main(String[] args) {
        SpringApplication.run(McpServerApplication.class, args);
    }
}

package com.logmonitor.log.storage;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * ES 索引模板初始化：为 log-entry-* 按天索引定义正式 mapping。
 * <p>
 * 生产必须用模板而不是动态映射：
 * logTime 映射为 date 才能做时间范围/排序，精确字段映射为 keyword 才能 term 查询，
 * content 保持 text 做全文检索。ES 不可用时仅记 WARN，不影响主链路。
 */
@Slf4j
@Component
public class EsIndexTemplateInitializer implements ApplicationRunner {

    private static final String TEMPLATE_NAME = "log-entry-template";
    private static final String TEMPLATE_JSON = """
            {
              "index_patterns": ["log-entry-*"],
              "priority": 100,
              "template": {
                "settings": {
                  "number_of_shards": 1,
                  "number_of_replicas": 0
                },
                "mappings": {
                  "properties": {
                    "id": {"type": "long"},
                    "fileName": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "filePath": {"type": "keyword"},
                    "logLevel": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "logTime": {"type": "date"},
                    "threadName": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "className": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "content": {"type": "text"},
                    "stackTrace": {"type": "text"},
                    "traceId": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "serviceName": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "logSource": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "createTime": {"type": "date"},
                    "srcIp": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "dstIp": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "srcPort": {"type": "integer"},
                    "dstPort": {"type": "integer"},
                    "protocol": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "action": {
                      "type": "text",
                      "fields": {"keyword": {"type": "keyword", "ignore_above": 256}}
                    },
                    "severity": {"type": "integer"}
                  }
                }
              }
            }
            """;

    private final EsProperties properties;

    public EsIndexTemplateInitializer(EsProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled()) {
            return;
        }
        try (RestClient restClient = RestClient.builder(HttpHost.create(properties.getUris())).build()) {
            Request request = new Request("PUT", "/_index_template/" + TEMPLATE_NAME);
            request.setJsonEntity(TEMPLATE_JSON);
            org.elasticsearch.client.Response response = restClient.performRequest(request);
            log.info("ES 索引模板 {} 初始化完成: {}", TEMPLATE_NAME, response.getStatusLine());
        } catch (Exception e) {
            log.warn("ES 索引模板初始化失败（不影响写入）: {}", e.getMessage());
        }
    }
}

package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;

/**
 * 默认 ES 客户端提供者：懒加载、单例共享。
 * <p>
 * ObjectMapper 注册 JavaTimeModule，LocalDateTime 以 ISO 字符串写入/读取，
 * 与 ES date 类型映射对齐。
 */
@Slf4j
@Component
public class DefaultEsClientProvider implements EsClientProvider {

    private final EsProperties properties;
    private volatile ElasticsearchClient client;

    public DefaultEsClientProvider(EsProperties properties) {
        this.properties = properties;
    }

    @Override
    public ElasticsearchClient getClient() {
        if (client == null) {
            synchronized (this) {
                if (client == null) {
                    HttpHost host = HttpHost.create(properties.getUris());
                    RestClient restClient = RestClient.builder(host).build();
                    ObjectMapper mapper = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
                    client = new ElasticsearchClient(
                            new RestClientTransport(restClient, new JacksonJsonpMapper(mapper)));
                    log.info("ES 客户端已初始化: {}", properties.getUris());
                }
            }
        }
        return client;
    }
}

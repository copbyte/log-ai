package com.logmonitor.log.storage;

import co.elastic.clients.elasticsearch.ElasticsearchClient;

/**
 * ES 客户端提供者：写入与查询共享同一个客户端，避免重复构建。
 */
public interface EsClientProvider {

    ElasticsearchClient getClient();
}

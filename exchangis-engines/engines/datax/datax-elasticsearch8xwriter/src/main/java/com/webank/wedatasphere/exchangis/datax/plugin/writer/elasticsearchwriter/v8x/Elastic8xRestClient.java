/*
 *
 *  Copyright 2020 WeBank
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch._types.Time;
import co.elastic.clients.elasticsearch._types.WaitForActiveShards;
import co.elastic.clients.elasticsearch.cluster.HealthRequest;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingRequest;
import co.elastic.clients.elasticsearch.indices.GetMappingResponse;
import co.elastic.clients.elasticsearch.indices.PutMappingRequest;
import co.elastic.clients.elasticsearch.indices.PutMappingResponse;
import co.elastic.clients.elasticsearch.indices.get_mapping.IndexMappingRecord;
import co.elastic.clients.json.JsonData;
import co.elastic.clients.json.JsonpUtils;
import co.elastic.clients.transport.BackoffPolicy;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.alibaba.datax.common.exception.DataXException;
import com.webank.wedatasphere.exchangis.datax.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.ssl.SSLContexts;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ES8 REST客户端封装类 / ES8 REST client wrapper
 *
 * 核心职责 / Core responsibilities:
 * - 封装ES8 ElasticsearchClient创建逻辑（认证、多节点）/ Wrap ES8 client creation (auth, multi-node)
 * - 提供索引操作API（exists、create、delete）/ Provide index operations API
 * - 提供Mapping管理API / Provide mapping management API
 *
 * 参考ES6的ElasticRestClient设计 / Reference ES6 ElasticRestClient design
 *
 * @author davidhua
 * @since 2024-03-12
 */
public class Elastic8xRestClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(Elastic8xRestClient.class);

    // 默认超时配置（毫秒）/ Default timeout configurations (ms)
    private static final int DEFAULT_SOCKET_TIMEOUT_MS = 60000;
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;
    private static final int DEFAULT_REQUEST_TIMEOUT_MS = 60000;
    private static final int DEFAULT_MASTER_TIMEOUT_MS = 30000;
    private static final long DEFAULT_BACKOFF_DELAY_MILLS = 1000L;
    private static final int DEFAULT_BACKOFF_TIMES = 3;
    private static final String FIELD_PROPS = "properties";
    private static final RequestOptions COMMON_OPTIONS;

    static {
        // 配置通用选项 / Configure common options
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
        COMMON_OPTIONS = builder.build();
    }

    private final ElasticsearchClient esClient;
    private final RestClientTransport transport;
    private final Map<String, Object> clientConfig;
    private final List<BulkIngester<?>> bulkIngesters = new ArrayList<>();
    /**
     * 私有构造函数 / Private constructor
     *
     * @param restClient Elasticsearch低级REST客户端 / Low-level REST client
     * @param transport REST传输层 / REST transport layer
     * @param clientConfig 客户端配置 / Client configuration
     */
    private Elastic8xRestClient(RestClient restClient, RestClientTransport transport,
                                Map<String, Object> clientConfig) {
        this.transport = transport;
        this.esClient = new ElasticsearchClient(transport);
        this.clientConfig = clientConfig;
    }

    /**
     * 工厂方法：创建无认证客户端 / Factory method: create client without auth
     *
     * @param endPoints ES节点地址数组 / ES endpoint addresses
     * @param clientConfig 客户端配置 / Client configuration
     * @return Elastic8xRestClient实例 / Elastic8xRestClient instance
     */
    public static Elastic8xRestClient custom(String[] endPoints, Map<String, Object> clientConfig) {
        try {
            RestClientBuilder builder = createRestClientBuilder(endPoints, null, null, clientConfig);
            RestClient restClient = builder.build();
            RestClientTransport transport = createTransport(restClient);
            return new Elastic8xRestClient(restClient, transport, clientConfig);
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BAD_CONNECT, e);
        }
    }

    /**
     * 工厂方法：创建带用户名密码认证的客户端 / Factory method: create client with username/password auth
     *
     * @param endPoints ES节点地址数组 / ES endpoint addresses
     * @param username 用户名 / Username
     * @param password 密码 / Password
     * @param clientConfig 客户端配置 / Client configuration
     * @return Elastic8xRestClient实例 / Elastic8xRestClient instance
     */
    public static Elastic8xRestClient custom(String[] endPoints, String username, String password,
                                             Map<String, Object> clientConfig) {
        try {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(username, password));
            RestClientBuilder builder = createRestClientBuilder(endPoints, credentialsProvider, null, clientConfig);
            RestClient restClient = builder.build();
            RestClientTransport transport = createTransport(restClient);
            return new Elastic8xRestClient(restClient, transport, clientConfig);
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BAD_CONNECT, e);
        }
    }

    /**
     * 工厂方法：创建SSL认证的客户端 / Factory method: create client with SSL auth
     *
     * @param endPoints ES节点地址数组 / ES endpoint addresses
     * @param keyStorePath 密钥库路径 / Keystore path
     * @param keyStorePass 密钥库密码 / Keystore password
     * @param clientConfig 客户端配置 / Client configuration
     * @return Elastic8xRestClient实例 / Elastic8xRestClient instance
     */
    public static Elastic8xRestClient sslCustom(String[] endPoints, String keyStorePath, String keyStorePass,
                                                Map<String, Object> clientConfig) {
        try {
            SSLContext sslContext = buildSSLContext(keyStorePath, keyStorePass);
            RestClientBuilder builder = createRestClientBuilder(endPoints, null, sslContext, clientConfig);
            RestClient restClient = builder.build();
            RestClientTransport transport = createTransport(restClient);
            return new Elastic8xRestClient(restClient, transport, clientConfig);
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BAD_CONNECT, e);
        }
    }

    /**
     * 检查索引是否存在 / Check if index exists
     *
     * @param indexName 索引名称 / Index name
     * @return true如果索引存在 / true if index exists
     */
    public boolean existIndices(String... indexName) {
        try {
            // 使用indices.exists()方法 / Use indices.exists() method
            ExistsRequest request = new ExistsRequest.Builder()
                    .index(java.util.Arrays.asList(indexName))
                    .build();
            return esClient.indices().exists(request).value();
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.REQUEST_ERROR,
                    "Failed to check index existence / 检查索引存在性失败: " + String.join(",", indexName), e);
        }
    }

    /**
     * 创建索引并设置Mapping / Create index and set mapping
     *
     * @param indexName 索引名称 / Index name
     * @param typeName 类型名称（ES8中已废弃，保留参数兼容）/ Type name (deprecated in ES8, kept for compatibility)
     * @param settings 索引设置 / Index settings
     * @param properties 字段映射 / Field mappings
     */
    public void createIndex(String indexName, String typeName, Map<String, Object> settings,
                           Map<Object, Object> properties) {
        try {
            // 如果索引不存在则创建 / Create index if not exists
            if (!existIndices(indexName)) {
                createIndex(indexName, settings);
            }
            // 设置Mapping / Set mapping
            if (properties != null && !properties.isEmpty()) {
                putMapping(indexName, properties);
            }
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.CREATE_INDEX_ERROR,
                    "Failed to create index / 创建索引失败: " + indexName, e);
        }
    }

    /**
     * 删除索引 / Delete indices
     *
     * @param indices 索引名称数组 / Index names
     * @return true如果删除成功 / true if deletion succeeded
     */
    public boolean deleteIndices(String... indices) {
        try {
            // 将数组转换为List / Convert array to List
            List<String> indexList = java.util.Arrays.asList(indices);
            DeleteIndexRequest request = new DeleteIndexRequest.Builder()
                    .index(indexList)
                    .build();
            return esClient.indices().delete(request).acknowledged();
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.DELETE_INDEX_ERROR,
                    "Failed to delete indices / 删除索引失败: " + String.join(",", indices), e);
        }
    }

    /**
     * 执行Bulk请求 / Execute bulk request
     *
     * @param bulkRequest Bulk请求对象 / Bulk request object
     * @return Bulk响应对象 / Bulk response object
     */
    public BulkResponse bulk(BulkRequest bulkRequest) {
        try {
            return esClient.bulk(bulkRequest);
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                    "Failed to execute bulk request / 执行Bulk请求失败", e);
        }
    }

    /**
     * 创建BulkIngester / Create BulkIngester
     *
     * ES8使用BulkIngester替代ES6的BulkProcessor / ES8 uses BulkIngester instead of ES6's BulkProcessor
     * BulkIngester自动管理批量大小、并发和重试 / BulkIngester automatically manages bulk size, concurrency and retries
     *
     * @param listener 监听器 / Listener
     * @param bulkActions 批量操作数量 / Bulk actions count
     * @param bulkPerTask 并发任务数 / Concurrent task count (ES8中自动管理,此参数保留但未使用)
     * @return BulkIngester实例 / BulkIngester instance
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <Context>BulkIngester<Context> createBulkIngester(BulkListener<Context> listener, int bulkActions, int bulkPerTask) {
        try {
            // 创建BulkIngester / Create BulkIngester
            // 使用ES8 Java Client的BulkIngester API / Use ES8 Java Client's BulkIngester API
            BulkIngester<Context> ingester = BulkIngester.of(b -> b
                    .client(this.esClient)
                    .maxOperations(bulkActions)
                    .maxConcurrentRequests(bulkPerTask)
                    .flushInterval(5, TimeUnit.SECONDS)
                    .backoffPolicy(BackoffPolicy.constantBackoff(DEFAULT_BACKOFF_DELAY_MILLS,
                            DEFAULT_BACKOFF_TIMES))
                    .listener(listener)
            );
            this.bulkIngesters.add(ingester);
            return ingester;
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                    "Failed to create BulkIngester / 创建BulkIngester失败", e);
        }
    }

    /**
     * 获取索引的Mapping字段属性 / Get index mapping field properties
     *
     * @param indexName 索引名称 / Index name
     * @param typeName 类型名称（ES8中已废弃）/ Type name (deprecated in ES8)
     * @return 字段属性Map / Field properties map
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Map<Object, Object> getProps(String indexName, String typeName) {
        try {
            GetMappingRequest request = new GetMappingRequest.Builder()
                    .index(indexName)
                    .build();
            GetMappingResponse response = esClient.indices().getMapping(request);
            IndexMappingRecord mappingRecord = response.get(indexName);
            if (null != mappingRecord){
                String mappingJson = JsonpUtils.toJsonString(mappingRecord, esClient._jsonpMapper());
                Map<String, Object> mappingMap = Json.fromJson(mappingJson, Map.class);
                if (null != mappingMap){
                    Object props = mappingMap.get(FIELD_PROPS);
                    return props instanceof Map? (Map)props : null;
                }
            }
            return null;
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.REQUEST_ERROR,
                    "Failed to get mapping / 获取Mapping失败: " + indexName, e);
        }
    }

    /**
     * 关闭客户端 / Close client
     */
    @Override
    public void close() {
        // 关闭所有BulkIngester / Close all BulkIngesters
        for (BulkIngester<?> ingester : bulkIngesters) {
            try {
                if (ingester != null) {
                    ingester.close();
                    LOG.debug("BulkIngester closed successfully / BulkIngester关闭成功");
                }
            } catch (Exception e) {
                LOG.error("Failed to close BulkIngester / 关闭BulkIngester失败", e);
            }
        }
        bulkIngesters.clear();

        // 关闭transport / Close transport
        try {
            if (transport != null) {
                transport.close();
            }
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.CLOSE_EXCEPTION,
                    "Failed to close ES client / 关闭ES客户端失败", e);
        }
    }

    /**
     * 创建索引 / Create index
     *
     * @param indexName 索引名称 / Index name
     * @param settings 索引设置 / Index settings
     */
    private void createIndex(String indexName, Map<String, Object> settings) throws IOException {
        if (settings == null) {
            settings = new HashMap<>(0);
        }

        // 创建CreateIndexRequest / Create CreateIndexRequest
        CreateIndexRequest.Builder requestBuilder = new CreateIndexRequest.Builder()
                .index(indexName);

        try {
            CreateIndexResponse response = esClient.indices().create(requestBuilder.build());
            if (!response.acknowledged()) {
                throw DataXException.asDataXException(Elastic8xWriterErrorCode.CREATE_INDEX_ERROR,
                        "Failed to create index, acknowledged=false / 创建索引失败, acknowledged=false: " + indexName);
            }
        } catch (Exception e) {
            // 索引可能已存在，记录warning / Index might already exist, log warning
            LOG.warn("Index: [{}] might already existed / 索引[{}]可能已存在", indexName, indexName, e);
        }
    }

    /**
     * 设置Mapping / Put mapping
     *
     * @param indexName 索引名称 / Index name
     * @param properties 字段属性 / Field properties
     */
    private void putMapping(String indexName, Map<Object, Object> properties) throws IOException {
        if (properties == null || properties.isEmpty()) {
            properties = new HashMap<>(0);
        }

        // 构建mapping结构 / Build mapping structure
        Map<String, Object> mappings = new HashMap<>(1);
        mappings.put(FIELD_PROPS, properties);

        PutMappingRequest request = new PutMappingRequest.Builder()
                .index(indexName)
                .withJson(new java.io.StringReader(Json.toJson(mappings, Map.class)))
                .build();

        PutMappingResponse response = esClient.indices().putMapping(request);
        if (!response.acknowledged()) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.PUT_MAPPINGS_ERROR,
                    "Failed to put mapping / 设置Mapping失败, index: " + indexName +
                            ", properties: " + Json.toJson(properties, Map.class));
        }
    }

    /**
     * 创建RestClientBuilder / Create RestClientBuilder
     *
     * @param endPoints ES节点地址 / ES endpoints
     * @param credentialsProvider 认证提供者 / Credentials provider
     * @param sslContext SSL上下文 / SSL context
     * @param clientConfig 客户端配置 / Client configuration
     * @return RestClientBuilder实例 / RestClientBuilder instance
     */
    private static RestClientBuilder createRestClientBuilder(String[] endPoints,
                                                             CredentialsProvider credentialsProvider,
                                                             SSLContext sslContext,
                                                             Map<String, Object> clientConfig) {
        // 创建HttpHost数组 / Create HttpHost array
        HttpHost[] httpHosts = new HttpHost[endPoints.length];
        for (int i = 0; i < endPoints.length; i++) {
            httpHosts[i] = HttpHost.create(endPoints[i]);
        }

        RestClientBuilder builder = RestClient.builder(httpHosts);

        // 配置超时 / Configure timeouts
        int socketTimeout = getIntConfig(clientConfig, Elastic8xKey.CLIENT_CONFIG_TIMEOUT_MS, DEFAULT_SOCKET_TIMEOUT_MS);
        int connectTimeout = getIntConfig(clientConfig, Elastic8xKey.CLIENT_CONFIG_CONN_TIMEOUT_MS, DEFAULT_CONNECT_TIMEOUT_MS);

        builder.setRequestConfigCallback(requestConfigBuilder -> {
            requestConfigBuilder.setConnectTimeout(connectTimeout);
            requestConfigBuilder.setSocketTimeout(socketTimeout);
            return requestConfigBuilder;
        });

        // 配置认证 / Configure auth
        if (credentialsProvider != null) {
            builder.setHttpClientConfigCallback(httpClientBuilder -> {
                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider);
                if (sslContext != null) {
                    httpClientBuilder.setSSLContext(sslContext);
                }
                return httpClientBuilder;
            });
        } else if (sslContext != null) {
            builder.setHttpClientConfigCallback(httpClientBuilder ->
                    httpClientBuilder.setSSLContext(sslContext));
        }

        return builder;
    }

    /**
     * 创建RestClientTransport / Create RestClientTransport
     *
     * @param restClient RestClient实例 / RestClient instance
     * @return RestClientTransport实例 / RestClientTransport instance
     */
    private static RestClientTransport createTransport(RestClient restClient) {
        // 使用Jackson提供的JsonpMapper / Use Jackson-provided JsonpMapper
        co.elastic.clients.json.jackson.JacksonJsonpMapper mapper =
                new co.elastic.clients.json.jackson.JacksonJsonpMapper(Json.getMapper());

        return new RestClientTransport(restClient, mapper);
    }

    /**
     * 构建SSL上下文 / Build SSL context
     *
     * @param keyStorePath 密钥库路径 / Keystore path
     * @param keyStorePass 密钥库密码 / Keystore password
     * @return SSLContext实例 / SSLContext instance
     */
    private static SSLContext buildSSLContext(String keyStorePath, String keyStorePass) {
        try {
            KeyStore truststore = KeyStore.getInstance("jks");
            try (InputStream inputStream = Files.newInputStream(Paths.get(new URI(keyStorePath)))) {
                truststore.load(inputStream, keyStorePass.toCharArray());
            } catch (URISyntaxException | IOException | NoSuchAlgorithmException |
                     CertificateException e) {
                throw DataXException.asDataXException(Elastic8xWriterErrorCode.BAD_CONNECT,
                        "Failed to build SSL context / 构建SSL上下文失败", e);
            }
            SSLContextBuilder sslContextBuilder = SSLContexts.custom()
                    .loadTrustMaterial(truststore, null);
            return sslContextBuilder.build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BAD_CONNECT,
                    "Failed to build SSL context / 构建SSL上下文失败", e);
        }
    }

    /**
     * 从配置中获取整数值 / Get integer value from config
     *
     * @param config 配置Map / Configuration map
     * @param key 配置键 / Configuration key
     * @param defaultValue 默认值 / Default value
     * @return 整数值 / Integer value
     */
    private static int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        if (config == null || !config.containsKey(key)) {
            return defaultValue;
        }
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

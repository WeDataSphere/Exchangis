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

import co.elastic.clients.elasticsearch._helpers.bulk.BulkIngester;
import co.elastic.clients.elasticsearch._helpers.bulk.BulkListener;
import co.elastic.clients.elasticsearch._types.ErrorCause;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperationVariant;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.element.StringColumn;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.BasicDataReceiver;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;
import com.alibaba.datax.core.statistics.plugin.task.util.DirtyRecord;
import com.webank.wedatasphere.exchangis.datax.common.CryptoUtils;
import com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.column.Elastic8xColumn;
import com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.column.Elastic8xFieldDataType;
import com.webank.wedatasphere.exchangis.datax.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * ES8 Writer插件 / ES8 Writer Plugin
 *
 * 核心职责 / Core responsibilities:
 * - 支持ES8数据写入 / Support ES8 data writing
 * - 使用BulkIngester批量写入 / Use BulkIngester for batch writing
 * - 支持向量类型 / Support vector type
 *
 * 参考ES6的ElasticWriter设计 / Reference ES6 ElasticWriter design
 *
 * @author davidhua
 * @since 2024-03-12
 */
public class Elastic8xWriter extends Writer {

    public static class Job extends Writer.Job {
        private static final Logger LOG = LoggerFactory.getLogger(Job.class);

        private static final String DEFAULT_ID = "_id";
        static final String WRITE_SIZE = "WRITE_SIZE";

        static final String DEFAULT_ENDPOINT_SPLIT = ",";

        private Configuration jobConf = null;
        private String[] endPoints;
        private String userName;
        private String password;

        @Override
        public void init() {
            // 初始化配置 / Initialize configuration
            this.jobConf = super.getPluginJobConf();
            // 参数校验 / Validate parameters
            this.validateParams();
        }

        @Override
        public void prepare() {
            // 获取secure配置,判断是否使用HTTPS / Get secure config to determine if using HTTPS
            boolean secure = jobConf.getBool(Elastic8xKey.SECURE, false);

            // 如果secure为true,将所有endPoint的schema变成https / If secure is true, change all endPoint schemas to https
            String[] processedEndPoints = endPoints;
            if (secure) {
                processedEndPoints = new String[endPoints.length];
                for (int i = 0; i < endPoints.length; i++) {
                    String endPoint = endPoints[i].trim();
                    // 替换http://为https:// / Replace http:// with https://
                    if (endPoint.startsWith("http://")) {
                        processedEndPoints[i] = "https://" + endPoint.substring(7);
                    } else if (!endPoint.startsWith("https://")) {
                        // 如果没有schema前缀,添加https:// / If no schema prefix, add https://
                        processedEndPoints[i] = "https://" + endPoint;
                    } else {
                        processedEndPoints[i] = endPoint;
                    }
                }
            }

            // 创建ES8客户端 / Create ES8 client
            Elastic8xRestClient restClient;
            Map<String, Object> clientConfig = jobConf.getMap(Elastic8xKey.CLIENT_CONFIG);

            // 如果secure为true,使用SSL连接 / If secure is true, use SSL connection
            if (secure) {
                String keyStorePath = jobConf.getString(Elastic8xKey.KEYSTORE_PATH, "");
                String keyStorePassword = jobConf.getString(Elastic8xKey.KEYSTORE_PASSWORD, "");

                // 如果提供了keystore,使用sslCustom / If keystore is provided, use sslCustom
                if (StringUtils.isNotBlank(keyStorePath)) {
                    if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                        restClient = Elastic8xRestClient.sslCustom(processedEndPoints, userName, password,
                                keyStorePath, keyStorePassword, clientConfig);
                    } else {
                        restClient = Elastic8xRestClient.sslCustom(processedEndPoints, keyStorePath,
                                keyStorePassword, clientConfig);
                    }
                } else {
                    // 没有keystore,使用普通custom(但URL已是https) / No keystore, use custom (but URL is already https)
                    if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                        restClient = Elastic8xRestClient.custom(processedEndPoints, userName, password, clientConfig);
                    } else {
                        restClient = Elastic8xRestClient.custom(processedEndPoints, clientConfig);
                    }
                }
            } else {
                // 非secure模式 / Non-secure mode
                if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                    restClient = Elastic8xRestClient.custom(processedEndPoints, userName, password, clientConfig);
                } else {
                    restClient = Elastic8xRestClient.custom(processedEndPoints, clientConfig);
                }
            }

            try {
                // 获取索引配置 / Get index configuration
                String indexName = this.jobConf.getNecessaryValue(Elastic8xKey.INDEX_NAME,
                        Elastic8xWriterErrorCode.REQUIRE_VALUE);
                String indexType = this.jobConf.getString(Elastic8xKey.INDEX_TYPE, "");
                String columnNameSeparator = this.jobConf.getString(Elastic8xKey.COLUMN_NAME_SEPARATOR,
                        Elastic8xColumn.DEFAULT_NAME_SPLIT);

                // 判断indexName是否包含pattern（同时包含"{"和"}"）/ Check if indexName has pattern (contains both "{" and "}")
                boolean hasPattern = indexName.contains(Elastic8xKey.INDEX_PATTERN_START)
                        && indexName.contains(Elastic8xKey.INDEX_PATTERN_END);

                // 获取allowIndexNotExist配置（默认false）/ Get allowIndexNotExist config (default false)
                boolean allowIndexNotExist = jobConf.getBool(Elastic8xKey.ALLOW_INDEX_NOT_EXIST, false);

                // 获取autoCreateIndex配置（默认false）/ Get autoCreateIndex config (default false)
                boolean autoCreateIndex = jobConf.getBool(Elastic8xKey.AUTO_CREATE_INDEX, false);

                // 判断索引是否存在 / Check if index exists
                boolean existsIndex = restClient.existIndices(indexName);

                // 计算是否需要构建索引 / Calculate if need to build index
                boolean needToBuildIndex = (jobConf.getBool(Elastic8xKey.CLEANUP, false) || !existsIndex)
                        && !allowIndexNotExist;

                // 如果需要构建索引但autoCreateIndex不为true,抛出异常 / If need to build index but autoCreateIndex is not true, throw exception
                if (needToBuildIndex && !autoCreateIndex) {
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.CONFIG_ERROR,
                            "Index needs to be built but autoCreateIndex is false / 索引需要构建但autoCreateIndex为false, index: [" + indexName + "]");
                }

                // 解析字段配置 / Resolve column configuration
                List<Object> rawColumnList = jobConf.getList(Elastic8xKey.PROPS_COLUMN);
                List<Elastic8xColumn> resolvedColumnList = new ArrayList<>();

                Map<Object, Object> props = resolveColumn(restClient, indexName, indexType,
                        rawColumnList, resolvedColumnList, columnNameSeparator, hasPattern, needToBuildIndex);
                this.jobConf.set(Elastic8xKey.PROPS_COLUMN, resolvedColumnList);

                // 只有在没有pattern的情况下才进行索引清理和创建 / Only cleanup and create index when no pattern
                if (!hasPattern) {
                    // 清理已存在的索引 / Cleanup existing index if configured
                    boolean cleanup = jobConf.getBool(Elastic8xKey.CLEANUP, false);
                    if (cleanup && existsIndex) {
                        if (!restClient.deleteIndices(indexName)) {
                            throw DataXException.asDataXException(Elastic8xWriterErrorCode.DELETE_INDEX_ERROR,
                                    "Failed to delete index / 删除索引失败: [" + indexName + "]");
                        }
                    }

                    // 创建索引（如果需要构建索引且允许自动创建）/ Create index (if need to build and allow auto create)
                    if (needToBuildIndex) {
                        restClient.createIndex(indexName, indexType, jobConf.getMap(Elastic8xKey.SETTINGS), props);
                    }
                }
            } finally {
                // 关闭客户端 / Close client
                restClient.close();
            }
        }

        @Override
        public List<Configuration> split(int mandatoryNumber) {
            // 任务分片 / Split tasks
            List<Configuration> configurations = new ArrayList<>();
            for (int i = 0; i < mandatoryNumber; i++) {
                configurations.add(this.jobConf.clone());
            }
            return configurations;
        }

        @Override
        public void destroy() {
            // 资源释放 / Resource cleanup
            // Job阶段无需特殊清理 / No special cleanup needed in Job phase
        }

        /**
         * 参数校验 / Validate parameters
         */
        private void validateParams() {
            // 校验endPoints / Validate endPoints
            String endPointsStr = this.jobConf.getString(Elastic8xKey.ENDPOINTS);
            if (StringUtils.isBlank(endPointsStr)) {
                throw DataXException.asDataXException(Elastic8xWriterErrorCode.REQUIRE_VALUE,
                        "Parameter 'endPoints(elasticUrls)' is required / 参数'endPoints(elasticUrls)'是必须的");
            }
            this.endPoints = endPointsStr.split(DEFAULT_ENDPOINT_SPLIT);

            // 获取用户名密码 / Get username and password
            this.userName = this.jobConf.getString(Elastic8xKey.USERNAME, "");
            this.password = this.jobConf.getString(Elastic8xKey.PASSWORD, "");

            // 解密密码 / Decrypt password
            if (StringUtils.isNotBlank(this.password)) {
                try {
                    this.password = (String) CryptoUtils.string2Object(this.password);
                } catch (Exception e) {
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.CONFIG_ERROR,
                            "Failed to decrypt password / 解密密码失败", e);
                }
            }

            // 校验索引名称 / Validate index name
            this.jobConf.getNecessaryValue(Elastic8xKey.INDEX_NAME, Elastic8xWriterErrorCode.REQUIRE_VALUE);
        }

        /**
         * 解析字段配置 / Resolve column configuration
         *
         * @param client ES8客户端 / ES8 client
         * @param index 索引名称 / Index name
         * @param type 类型名称 / Type name
         * @param rawColumnList 原始字段列表 / Raw column list
         * @param outputColumn 输出字段列表 / Output column list
         * @param columnNameSeparator 字段分隔符 / Column separator
         * @param hasPattern 索引名是否包含pattern / Whether index name has pattern
         * @param needToBuildIndex 是否需要构建索引 / Whether need to build index
         * @return 字段属性Map / Field properties map
         */
        private Map<Object, Object> resolveColumn(Elastic8xRestClient client,
                                                   String index, String type,
                                                   List<Object> rawColumnList, List<Elastic8xColumn> outputColumn,
                                                   String columnNameSeparator, boolean hasPattern, boolean needToBuildIndex) {
            Map<Object, Object> properties;

            if (null != rawColumnList && !rawColumnList.isEmpty()) {
                // 用户自定义字段 / User-defined columns
                properties = new HashMap<>(rawColumnList.size());
                rawColumnList.forEach(columnRaw -> {
                    String raw = Json.toJson(columnRaw, Map.class);
                    Elastic8xColumn column = Json.fromJson(raw, Elastic8xColumn.class);

                    // 对于用户自定义字段,column.getType()可能为空,这里不判断type是否为空
                    // For user-defined columns, column.getType() may be null, don't check if type is blank here
                    if (StringUtils.isNotBlank(column.getName())) {
                        outputColumn.add(column);

                        // 排除_id字段和ALIAS类型,加入properties需同时满足needToBuildIndex和column.getType()不为空
                        // Exclude _id field and ALIAS type, add to properties only when needToBuildIndex and column.getType() is not blank
                        if (!column.getName().equals(DEFAULT_ID)
                                && StringUtils.isNotBlank(column.getType())
                                && needToBuildIndex
                                && Elastic8xFieldDataType.valueOf(column.getType().toUpperCase())
                                != Elastic8xFieldDataType.ALIAS) {
                            Map property = Json.fromJson(raw, Map.class);
                            property.remove(Elastic8xKey.PROPS_COLUMN_NAME);
                            properties.put(column.getName(), property);
                        }
                    }
                });
            } else {
                // 从已存在的索引获取字段 / Get columns from existing index
                // 抛出异常的条件: hasPattern || !client.existIndices(index)
                // Throw exception when: hasPattern || !client.existIndices(index)
                if (hasPattern || !client.existIndices(index)) {
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.INDEX_NOT_EXIST,
                            "Cannot get columns from index (hasPattern or not exists) / 无法从索引获取字段(包含pattern或不存在): [" + index + "]");
                }

                // 从索引获取properties / Get properties from index
                properties = client.getProps(index, type);
                resolveColumn(outputColumn, null, properties, columnNameSeparator);

                // 反转字段列表 / Reverse column list
                Collections.reverse(outputColumn);
            }

            return properties;
        }

        /**
         * 递归解析嵌套字段 / Recursively resolve nested columns
         *
         * @param outputColumn 输出字段列表 / Output column list
         * @param column 当前字段 / Current column
         * @param propsMap 属性Map / Properties map
         * @param columnNameSeparator 字段分隔符 / Column separator
         */
        private void resolveColumn(List<Elastic8xColumn> outputColumn, Elastic8xColumn column,
                                   Map<Object, Object> propsMap, String columnNameSeparator) {
            propsMap.forEach((key, value) -> {
                if (value instanceof Map) {
                    Map metaMap = (Map) value;

                    // 处理有type字段的属性 / Handle properties with type field
                    if (null != metaMap.get(Elastic8xKey.PROPS_COLUMN_TYPE)) {
                        Elastic8xColumn levelColumn = new Elastic8xColumn();
                        if (null != column) {
                            levelColumn.setName(column.getName() + columnNameSeparator + key);
                        } else {
                            levelColumn.setName(String.valueOf(key));
                        }

                        levelColumn.setType(String.valueOf(metaMap.get(Elastic8xKey.PROPS_COLUMN_TYPE)));

                        if (null != metaMap.get(Elastic8xKey.PROPS_COLUMN_TIMEZONE)) {
                            levelColumn.setTimezone(String.valueOf(metaMap.get(Elastic8xKey.PROPS_COLUMN_TIMEZONE)));
                        }

                        if (null != metaMap.get(Elastic8xKey.PROPS_COLUMN_FORMAT)) {
                            levelColumn.setFormat(String.valueOf(metaMap.get(Elastic8xKey.PROPS_COLUMN_FORMAT)));
                        }

                        if (null != metaMap.get(Elastic8xKey.PROPS_COLUMN_DIMS)) {
                            Object dimsObj = metaMap.get(Elastic8xKey.PROPS_COLUMN_DIMS);
                            if (dimsObj instanceof Number) {
                                levelColumn.setDims(((Number) dimsObj).intValue());
                            }
                        }

                        outputColumn.add(levelColumn);
                    } else if (null != metaMap.get(Elastic8xKey.FIELD_PROPS)
                            && metaMap.get(Elastic8xKey.FIELD_PROPS) instanceof Map) {
                        // 处理嵌套的properties字段 / Handle nested properties field
                        Elastic8xColumn levelColumn = column;
                        if (null == levelColumn) {
                            levelColumn = new Elastic8xColumn();
                            levelColumn.setName(String.valueOf(key));
                        } else {
                            levelColumn.setName(levelColumn.getName() + columnNameSeparator + key);
                        }

                        resolveColumn(outputColumn, levelColumn, (Map) metaMap.get(Elastic8xKey.FIELD_PROPS),
                                columnNameSeparator);
                    }
                }
            });
        }
    }

    /**
     * Task类 - 数据写入核心逻辑 / Task class - Core data writing logic
     *
     * 核心职责 / Core responsibilities:
     * - 初始化ES8客户端和BulkIngester / Initialize ES8 client and BulkIngester
     * - 从RecordReceiver读取数据 / Read data from RecordReceiver
     * - 转换数据为ES8文档格式 / Convert data to ES8 document format
     * - 批量写入ES8 / Bulk write to ES8
     * - 处理写入错误和脏数据 / Handle write errors and dirty data
     */
    public static class Task extends Writer.Task {
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        static final String DEFAULT_ENDPOINT_SPLIT = ",";

        private volatile boolean bulkError;
        private Configuration taskConf;
        private String indexName;
        private String typeName;
        private String columnNameSeparator = Elastic8xColumn.DEFAULT_NAME_SPLIT;
        private List<Elastic8xColumn> columns;
        private Elastic8xRestClient restClient;
        private BulkIngester<Object> bulkIngester;

        // 配置项 / Configuration items
        private int batchSize;
        private int bulkPerTask;
        private boolean secure;
        private String userName;
        private String password;
        private String keyStorePath;
        private String keyStorePassword;
        private String dateFormat;
        private boolean allowIndexNotExist;

        @Override
        public void init() {
            // 初始化配置 / Initialize configuration
            this.taskConf = super.getPluginJobConf();

            // 获取基本配置 / Get basic configuration
            indexName = this.taskConf.getString(Elastic8xKey.INDEX_NAME);
            typeName = this.taskConf.getString(Elastic8xKey.INDEX_TYPE, "");
            columnNameSeparator = this.taskConf.getString(Elastic8xKey.COLUMN_NAME_SEPARATOR,
                    Elastic8xColumn.DEFAULT_NAME_SPLIT);
            batchSize = this.taskConf.getInt(Elastic8xKey.BULK_ACTIONS, 1000);
            bulkPerTask = this.taskConf.getInt(Elastic8xKey.BULK_PER_TASK, 1);

            // 获取字段配置 / Get column configuration
            String columnsJson = this.taskConf.getString(Elastic8xKey.PROPS_COLUMN);
            if (StringUtils.isNotBlank(columnsJson)) {
                columns = Json.fromJson(columnsJson, List.class, Elastic8xColumn.class);
            } else {
                columns = new ArrayList<>();
            }

            // 获取认证信息 / Get authentication information
            userName = this.taskConf.getString(Elastic8xKey.USERNAME, "");
            password = this.taskConf.getString(Elastic8xKey.PASSWORD, "");
            if (StringUtils.isNotBlank(password)) {
                try {
                    password = (String) CryptoUtils.string2Object(password);
                } catch (Exception e) {
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.CONFIG_ERROR,
                            "Failed to decrypt password / 解密密码失败", e);
                }
            }

            // 获取SSL配置 / Get SSL configuration
            secure = this.taskConf.getBool(Elastic8xKey.SECURE, false);
            keyStorePath = this.taskConf.getString(Elastic8xKey.KEYSTORE_PATH, "");
            keyStorePassword = this.taskConf.getString(Elastic8xKey.KEYSTORE_PASSWORD, "");

            // 获取其他配置 / Get other configuration
            dateFormat = this.taskConf.getString(Elastic8xKey.DATE_FORMAT, "");
            allowIndexNotExist = this.taskConf.getBool(Elastic8xKey.ALLOW_INDEX_NOT_EXIST, false);

            // 获取endPoints并处理HTTPS / Get endPoints and process HTTPS
            String[] endPoints = this.taskConf.getString(Elastic8xKey.ENDPOINTS).split(DEFAULT_ENDPOINT_SPLIT);
            String[] processedEndPoints = endPoints;
            if (secure) {
                processedEndPoints = new String[endPoints.length];
                for (int i = 0; i < endPoints.length; i++) {
                    String endPoint = endPoints[i].trim();
                    if (endPoint.startsWith("http://")) {
                        processedEndPoints[i] = "https://" + endPoint.substring(7);
                    } else if (!endPoint.startsWith("https://")) {
                        processedEndPoints[i] = "https://" + endPoint;
                    } else {
                        processedEndPoints[i] = endPoint;
                    }
                }
            }

            // 创建ES8客户端 / Create ES8 client
            Map<String, Object> clientConfig = this.taskConf.getMap(Elastic8xKey.CLIENT_CONFIG);
            if (secure && StringUtils.isNotBlank(keyStorePath)) {
                // SSL with keystore / 使用keystore的SSL
                if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                    restClient = Elastic8xRestClient.sslCustom(processedEndPoints, userName, password,
                            keyStorePath, keyStorePassword, clientConfig);
                } else {
                    restClient = Elastic8xRestClient.sslCustom(processedEndPoints, keyStorePath,
                            keyStorePassword, clientConfig);
                }
            } else {
                // Non-SSL or SSL without keystore / 非SSL或无keystore的SSL
                if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                    restClient = Elastic8xRestClient.custom(processedEndPoints, userName, password, clientConfig);
                } else {
                    restClient = Elastic8xRestClient.custom(processedEndPoints, clientConfig);
                }
            }

            // 创建BulkIngester / Create BulkIngester
            this.bulkIngester = restClient.createBulkIngester(
                    buildBulkListener(getTaskPluginCollector()), batchSize, bulkPerTask);

            LOG.info("Elastic8x Writer Task initialized / Elastic8x Writer Task初始化成功, index: {}", indexName);
        }

        @Override
        public void startWrite(RecordReceiver recordReceiver) {
            LOG.info("Begin to write record to ElasticSearch / 开始向ElasticSearch写入记录, index: {}", indexName);

            Record record = null;
            long count = 0;

            try {
                while (null != (record = recordReceiver.getFromReader())) {
                    // 检查是否有bulk错误 / Check if bulk error occurred
                    if (bulkError) {
                        throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                                "Bulk operation failed / 批量操作失败");
                    }

                    // 转换Record为ES8文档 / Convert Record to ES8 document
                    Map<String, Object> data = Elastic8xColumn.toData(record, columns, columnNameSeparator,
                            dateFormat, allowIndexNotExist);

                    // 添加到BulkIngester / Add to BulkIngester
                    // 使用BulkOperation.Builder构建索引操作 / Use BulkOperation.Builder to build index operation
                    bulkIngester.add(bulkOperationBuilder -> bulkOperationBuilder
                            .index(idx -> idx
                                    .index(indexName)
                                    .document(data)
                            ), null);
                    count += 1;
                }

                // 记录写入数量 / Record write count
                getTaskPluginCollector().collectMessage(Job.WRITE_SIZE, String.valueOf(count));
                LOG.info("End to write record to ElasticSearch / 向ElasticSearch写入记录结束, total: {}", count);

            } catch (Exception e) {
                LOG.error("Failed to write record / 写入记录失败", e);
                throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                        "Failed to write record / 写入记录失败", e);
            }
        }

        @Override
        public void startWrite(BasicDataReceiver<Object> receiver, Class<?> type) {
            // 支持BulkOperationVariant模式 / Support BulkOperationVariant mode
            // ES8中使用BulkOperationVariant (对应ES6的DocWriteRequest)
            // ES8 uses BulkOperationVariant (corresponds to ES6's DocWriteRequest)
            if (BulkOperationVariant.class.isAssignableFrom(type)) {
                LOG.info("Begin to write BulkOperationVariant to ElasticSearch / 开始向ElasticSearch写入BulkOperationVariant, index: {}", indexName);

                BulkOperationVariant variant = null;
                long count = 0;

                try {
                    while (null != (variant = (BulkOperationVariant) receiver.getFromReader())) {
                        // 检查是否有bulk错误 / Check if bulk error occurred
                        if (bulkError) {
                            throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                                    "Bulk operation failed / 批量操作失败");
                        }

                        // 使用BulkOperation包裹variant后添加到BulkIngester
                        // Wrap variant with BulkOperation and add to BulkIngester
                        BulkOperation bulkOperation = new BulkOperation(variant);
                        bulkIngester.add(bulkOperation);

                        count += 1;
                    }

                    // 记录写入数量 / Record write count
                    getTaskPluginCollector().collectMessage(Job.WRITE_SIZE, String.valueOf(count));
                    LOG.info("End to write BulkOperationVariant to ElasticSearch / 向ElasticSearch写入BulkOperationVariant结束, total: {}", count);

                } catch (Exception e) {
                    LOG.error("Failed to write BulkOperationVariant / 写入BulkOperationVariant失败", e);
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.BULK_REQ_ERROR,
                            "Failed to write BulkOperationVariant / 写入BulkOperationVariant失败", e);
                }
            } else {
                // 不支持的类型，调用父类 / Unsupported type, call parent
                LOG.warn("Unsupported type in startWrite(BasicDataReceiver, Class): {}, calling super method", type);
                super.startWrite(receiver, type);
            }
        }

        @Override
        public void destroy() {
            // 关闭BulkIngester / Close BulkIngester
            if (null != bulkIngester) {
                try {
                    bulkIngester.close();
                    LOG.debug("BulkIngester closed successfully / BulkIngester关闭成功");
                } catch (Exception e) {
                    LOG.error("Failed to close BulkIngester / 关闭BulkIngester失败", e);
                }
            }

            // 关闭ES8客户端 / Close ES8 client
            if (null != restClient) {
                try {
                    restClient.close();
                    LOG.debug("ES8 client closed successfully / ES8客户端关闭成功");
                } catch (Exception e) {
                    LOG.error("Failed to close ES8 client / 关闭ES8客户端失败", e);
                }
            }
        }

        /**
         * 构建BulkListener / Build BulkListener
         *
         * @param pluginCollector DataX脏数据收集器 / DataX dirty data collector
         * @return BulkListener实例 / BulkListener instance
         */
        private BulkListener<Object> buildBulkListener(final TaskPluginCollector pluginCollector) {
            return new BulkListener<Object>() {
                @Override
                public void beforeBulk(long executionId, BulkRequest request, List<Object> contexts) {
                    LOG.trace("Before bulk operation / 批量操作前, executionId: {}, operations: {}",
                            executionId, request.operations().size());
                }

                @Override
                public void afterBulk(long executionId, BulkRequest request, List<Object> contexts, BulkResponse response) {
                    if (response.errors()) {
                        // 处理批量操作中的错误 / Handle errors in bulk operation
                        response.items().forEach(item -> {
                            if (item.error() != null) {
                                ErrorCause error = item.error();
                                List<String> message = new ArrayList<>();
                                message.add(item.id());
                                message.add(error.reason());

                                // 收集脏数据 / Collect dirty data
                                // 创建DirtyRecord记录错误信息 / Create DirtyRecord to log error message
                                DirtyRecord dirtyRecord = new DirtyRecord();
                                dirtyRecord.addColumn(new StringColumn(item.id()));
                                dirtyRecord.addColumn(new StringColumn(error.reason()));
                                pluginCollector.collectDirtyRecord(dirtyRecord, null,
                                        Json.toJson(message, null));
                            }
                        });
                    }
                    LOG.trace("After bulk operation / 批量操作后, executionId: {}, hasErrors: {}",
                            executionId, response.errors());
                }

                @Override
                public void afterBulk(long executionId, BulkRequest request, List<Object> contexts, Throwable failure) {
                    // 忽略中断错误 / Ignore interrupted error
                    if (!(failure instanceof InterruptedException)) {
                        LOG.error("Bulk operation failed / 批量操作失败, executionId: {}", executionId, failure);
                    }
                    bulkError = true;
                }
            };
        }
    }
}

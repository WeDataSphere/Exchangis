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

import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.plugin.BasicDataReceiver;
import com.alibaba.datax.common.plugin.RecordReceiver;
import com.alibaba.datax.common.plugin.TaskPluginCollector;
import com.alibaba.datax.common.spi.Writer;
import com.alibaba.datax.common.util.Configuration;
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
            // 创建ES8客户端 / Create ES8 client
            Elastic8xRestClient restClient;
            Map<String, Object> clientConfig = jobConf.getMap(Elastic8xKey.CLIENT_CONFIG);

            if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(password)) {
                restClient = Elastic8xRestClient.custom(endPoints, userName, password, clientConfig);
            } else {
                restClient = Elastic8xRestClient.custom(endPoints, clientConfig);
            }

            try {
                // 获取索引配置 / Get index configuration
                String indexName = this.jobConf.getNecessaryValue(Elastic8xKey.INDEX_NAME,
                        Elastic8xWriterErrorCode.REQUIRE_VALUE);
                String indexType = this.jobConf.getString(Elastic8xKey.INDEX_TYPE, "");
                String columnNameSeparator = this.jobConf.getString(Elastic8xKey.COLUMN_NAME_SEPARATOR,
                        Elastic8xColumn.DEFAULT_NAME_SPLIT);

                // 解析字段配置 / Resolve column configuration
                List<Object> rawColumnList = jobConf.getList(Elastic8xKey.PROPS_COLUMN);
                List<Elastic8xColumn> resolvedColumnList = new ArrayList<>();

                Map<Object, Object> props = resolveColumn(restClient, indexName, indexType,
                        rawColumnList, resolvedColumnList, columnNameSeparator);
                this.jobConf.set(Elastic8xKey.PROPS_COLUMN, resolvedColumnList);

                // 清理已存在的索引 / Cleanup existing index if configured
                if (jobConf.getBool(Elastic8xKey.CLEANUP, false) && restClient.existIndices(indexName)) {
                    if (!restClient.deleteIndices(indexName)) {
                        throw DataXException.asDataXException(Elastic8xWriterErrorCode.DELETE_INDEX_ERROR,
                                "Failed to delete index / 删除索引失败: [" + indexName + "]");
                    }
                }

                // 如果索引不存在,创建索引 / Create index if not exists
                restClient.createIndex(indexName, indexType, jobConf.getMap(Elastic8xKey.SETTINGS), props);
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
         * @return 字段属性Map / Field properties map
         */
        private Map<Object, Object> resolveColumn(Elastic8xRestClient client,
                                                   String index, String type,
                                                   List<Object> rawColumnList, List<Elastic8xColumn> outputColumn,
                                                   String columnNameSeparator) {
            Map<Object, Object> properties;

            if (null != rawColumnList && !rawColumnList.isEmpty()) {
                // 用户自定义字段 / User-defined columns
                properties = new HashMap<>(rawColumnList.size());
                rawColumnList.forEach(columnRaw -> {
                    String raw = Json.toJson(columnRaw, Map.class);
                    Elastic8xColumn column = Json.fromJson(raw, Elastic8xColumn.class);

                    if (StringUtils.isNotBlank(column.getName()) && StringUtils.isNotBlank(column.getType())) {
                        outputColumn.add(column);

                        // 排除_id字段和ALIAS类型 / Exclude _id field and ALIAS type
                        if (!column.getName().equals(DEFAULT_ID)
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
                if (!client.existIndices(index)) {
                    throw DataXException.asDataXException(Elastic8xWriterErrorCode.INDEX_NOT_EXIST,
                            "Index does not exist, cannot get columns / 索引不存在,无法获取字段: [" + index + "]");
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
     * Task类将在下一层实现 / Task class will be implemented in next layer
     */
    public static class Task extends Writer.Task {
        private static final Logger LOG = LoggerFactory.getLogger(Task.class);

        // Task类的实现将在第6层完成 / Task implementation will be completed in layer 6
        // TODO: 第6层实现 / Layer 6 implementation

        @Override
        public void init() {
            // TODO: 第6层实现 / Layer 6 implementation
            // 初始化配置 / Initialize configuration
            // 此方法将在第6层完整实现 / This method will be fully implemented in layer 6
        }

        @Override
        public void startWrite(RecordReceiver recordReceiver) {
            // TODO: 第6层实现 / Layer 6 implementation
            // 此方法将在第6层完整实现 / This method will be fully implemented in layer 6
            throw new UnsupportedOperationException("Task.startWrite() will be implemented in layer 6");
        }

        @Override
        public void destroy() {
            // TODO: 第6层实现 / Layer 6 implementation
            // 资源释放 / Resource cleanup
            // 此方法将在第6层完整实现 / This method will be fully implemented in layer 6
        }
    }
}

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

package com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.column;

import com.alibaba.datax.common.element.Column;
import com.alibaba.datax.common.element.Record;
import com.alibaba.datax.common.exception.DataXException;
import com.fasterxml.jackson.databind.JsonNode;
import com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.Elastic8xKey;
import com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.Elastic8xWriterErrorCode;
import com.webank.wedatasphere.exchangis.datax.util.Json;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elastic8x字段映射配置类
 *
 * 参考ES6的ElasticColumn设计，提供静态方法toData处理Record到ES8文档的转换
 *
 * 核心职责：
 * 1. 定义字段映射配置（name、type、format、timezone、dims）
 * 2. 提供静态方法toData，将Record转换为ES8文档
 * 3. 处理各种ES8数据类型（包括vector向量类型）
 *
 * @author davidhua
 * 2019/8/15
 */
public class Elastic8xColumn {

    private static final Logger LOG = LoggerFactory.getLogger(Elastic8xColumn.class);

    private static final String ARRAY_SUFFIX = "]";
    private static final String ARRAY_PREFIX = "[";

    public static final String DEFAULT_NAME_SPLIT = "\\.";

    private String name;

    private String type;

    private String format;

    private String timezone;

    private Integer dims; // 向量维度（仅dense_vector类型使用）

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public Integer getDims() {
        return dims;
    }

    public void setDims(Integer dims) {
        this.dims = dims;
    }

    /**
     * 将Record转换为ES8文档（Map结构）
     *
     * 参考ES6的ElasticColumn.toData方法设计
     *
     * @param record DataX Record对象
     * @param colConfs 字段配置列表
     * @param columnNameSeparator 嵌套字段分隔符
     * @param dataFormat 日期格式化pattern
     * @param allowIndexNotExist 是否允许索引不存在
     * @return ES8文档（Map结构）
     */
    public static Map<String, Object> toData(Record record, List<Elastic8xColumn> colConfs,
                                               String columnNameSeparator, String dataFormat,
                                               Boolean allowIndexNotExist) {
        Map<String, Object> outputData = new HashMap<>(record.getColumnNumber());

        // 根据dataFormat创建DateTimeFormatter
        DateTimeFormatter dateTimeFormatter = null;
        if (StringUtils.isNotBlank(dataFormat)) {
            try {
                dateTimeFormatter = DateTimeFormat.forPattern(dataFormat);
            } catch (Exception e) {
                LOG.warn("Invalid dataFormat pattern: {}, ignoring", dataFormat, e);
            }
        }

        for (int i = 0; i < record.getColumnNumber(); i++) {
            Column column = record.getColumn(i);
            Elastic8xColumn config = colConfs.get(i);
            String columnName = config.getName();

            // 处理嵌套对象
            Map<String, Object> innerOutput = outputData;
            String[] levelColumns = columnName.split(columnNameSeparator);
            if (levelColumns.length > 1) {
                columnName = levelColumns[levelColumns.length - 1];
                for (int j = 0; j < levelColumns.length - 1; j++) {
                    Map<String, Object> data = new HashMap<>();
                    innerOutput.put(levelColumns[j], data);
                    innerOutput = data;
                }
            }

            // 根据类型转换字段值 / Convert field value based on type
            Elastic8xFieldDataType dataType;

            // 如果配置的type为空且允许索引不存在,自动推测字段类型
            // If configured type is null and allowIndexNotExist is true, auto-detect field type
            if (StringUtils.isBlank(config.getType()) && allowIndexNotExist) {
                dataType = autoDetectFieldType(column);
                LOG.debug("Auto-detected field type for column '{}' as: {}", columnName, dataType);
            } else {
                // 使用配置的类型 / Use configured type
                try {
                    dataType = Elastic8xFieldDataType.valueOf(config.getType().toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Unknown type: {}, using TEXT as default / 未知类型: {}, 使用TEXT作为默认值", config.getType());
                    dataType = Elastic8xFieldDataType.TEXT;
                }
            }

            Object value;
            try {
                switch (dataType) {
                    case IP:
                    case IP_RANGE:
                    case KEYWORD:
                    case TEXT:
                        value = column.asString();
                        innerOutput.put(columnName, value);
                        break;

                    case GEO_POINT:
                    case GEO_SHAPE:
                    case NESTED:
                    case OBJECT:
                        value = parseObject(column.asString());
                        innerOutput.put(columnName, value);
                        break;

                    case LONG_RANGE:
                    case LONG:
                    case BYTE:
                        value = column.asLong();
                        innerOutput.put(columnName, value);
                        break;

                    case INTEGER:
                    case INTEGER_RANGE:
                    case SHORT:
                        value = column.asBigInteger();
                        innerOutput.put(columnName, value);
                        break;

                    case FLOAT:
                    case FLOAT_RANGE:
                    case HALF_FLOAT:
                    case SCALED_FLOAT:
                    case DOUBLE_RANGE:
                    case DOUBLE:
                        value = column.asDouble();
                        innerOutput.put(columnName, value);
                        break;

                    case BINARY:
                        value = column.asBytes();
                        innerOutput.put(columnName, value);
                        break;

                    case BOOLEAN:
                        value = column.asBoolean();
                        innerOutput.put(columnName, value);
                        break;

                    case DATE_RANGE:
                    case DATE:
                        value = parseDate(config, column, dateTimeFormatter);
                        innerOutput.put(columnName, value);
                        break;

                    case DENSE_VECTOR:
                        value = parseVector(column);
                        innerOutput.put(columnName, value);
                        break;

                    case SPARSE_VECTOR:
                        value = parseSparseVector(column);
                        innerOutput.put(columnName, value);
                        break;

                    case ALIAS:
                        // ALIAS is a metadata field, treat as string
                        value = column.asString();
                        innerOutput.put(columnName, value);
                        break;

                    default:
                        throw DataXException.asDataXException(Elastic8xWriterErrorCode.MAPPING_TYPE_UNSUPPORTED,
                                "unsupported type:[" + config.getType() + "]");
                }
            } catch (Exception e) {
                throw DataXException.asDataXException(Elastic8xWriterErrorCode.MAPPING_TYPE_UNSUPPORTED,
                        "Failed to convert column: " + columnName + ", type: " + config.getType() + ", value: " + column.asString(), e);
            }
        }

        return outputData;
    }

    /**
     * 自动推测字段类型 / Auto-detect field type based on Column type
     *
     * 映射规则 / Mapping rules:
     * - Type.INT => Elastic8xFieldDataType.INTEGER
     * - Type.LONG => Elastic8xFieldDataType.LONG
     * - Type.BOOLEAN => Elastic8xFieldDataType.BOOLEAN
     * - Type.BYTES => Elastic8xFieldDataType.BYTE
     * - Type.STRING => Elastic8xFieldDataType.TEXT
     * - Type.DATE => Elastic8xFieldDataType.DATE
     * - 其他 / Others: 尝试通过type name转换 / Try to convert via type name
     *
     * @param column DataX Column对象 / DataX Column object
     * @return 推测的字段类型 / Detected field type
     */
    private static Elastic8xFieldDataType autoDetectFieldType(Column column) {
        if (column == null || column.getType() == null) {
            LOG.warn("Column or its type is null, defaulting to TEXT / Column或其类型为null,默认使用TEXT");
            return Elastic8xFieldDataType.TEXT;
        }

        Column.Type columnType = column.getType();

        // 根据Column类型映射到ES8字段类型 / Map Column type to ES8 field type
        switch (columnType) {
            case INT:
                return Elastic8xFieldDataType.INTEGER;
            case LONG:
                return Elastic8xFieldDataType.LONG;
            case BOOLEAN:
                return Elastic8xFieldDataType.BOOLEAN;
            case BYTES:
                return Elastic8xFieldDataType.BYTE;
            case STRING:
                return Elastic8xFieldDataType.TEXT;
            case DATE:
                return Elastic8xFieldDataType.DATE;
            case DOUBLE:
                return Elastic8xFieldDataType.DOUBLE;
            default:
                // 尝试通过type name转换 / Try to convert via type name
                try {
                    return Elastic8xFieldDataType.valueOf(columnType.name().toUpperCase());
                } catch (IllegalArgumentException e) {
                    LOG.warn("Cannot map column type {} to ES8 field type, defaulting to TEXT / 无法将Column类型 {} 映射到ES8字段类型,默认使用TEXT",
                            columnType, columnType);
                    return Elastic8xFieldDataType.TEXT;
                }
        }
    }

    /**
     * 解析对象类型（OBJECT/NESTED）
     */
    private static Object parseObject(String rawData) {
        try {
            if (rawData.startsWith(ARRAY_PREFIX) && rawData.endsWith(ARRAY_SUFFIX)) {
                return Json.fromJson(rawData, Object.class);
            }
            return Json.fromJson(rawData, Map.class);
        } catch (Exception e) {
            LOG.warn("Failed to parse object: {}, returning raw string", rawData, e);
            return rawData;
        }
    }

    /**
     * 解析日期类型
     */
    private static String parseDate(Elastic8xColumn config, Column column, DateTimeFormatter dateTimeFormatter) {
        DateTimeZone dateTimeZone = DateTimeZone.getDefault();
        if (StringUtils.isNotBlank(config.getTimezone())) {
            dateTimeZone = DateTimeZone.forID(config.getTimezone());
        }
        String output;
        if (column.getType() == Column.Type.DATE) {
            DateTime dateTime = new DateTime(column.asLong(), dateTimeZone);
            // 如果传入了dateTimeFormatter，使用print()格式化输出
            if (dateTimeFormatter != null) {
                output = dateTimeFormatter.print(dateTime);
            } else {
                output = dateTime.toString();
            }
        } else if (StringUtils.isNotBlank(config.getFormat())) {
            DateTimeFormatter formatter = DateTimeFormat.forPattern(config.getFormat());
            DateTime dateTime = formatter.withZone(dateTimeZone)
                    .parseDateTime(column.asString());
            // 如果传入了dateTimeFormatter，使用print()格式化输出
            if (dateTimeFormatter != null) {
                output = dateTimeFormatter.print(dateTime);
            } else {
                output = dateTime.toString();
            }
        } else {
            output = column.asString();
        }
        return output;
    }

    /**
     * 解析向量类型（DENSE_VECTOR）
     * 支持格式：JSON数组字符串，如"[0.1, 0.2, 0.3]"
     * 支持数值类型和字符串类型值的自动转换
     * Support automatic conversion of numeric and string type values
     *
     * 使用decimalValue()直接获取BigDecimal，确保精度
     * Use decimalValue() to directly get BigDecimal, ensuring precision
     */
    private static double[] parseVector(Column column) {
        try {
            // 解析JSON数组字符串 / Parse JSON array string
            String rawData = column.asString();
            // 使用VectorJson的ObjectMapper，确保浮点数解析精度 / Use VectorJson's ObjectMapper to ensure precision
            JsonNode jsonNode = VectorJson.getMapper().readTree(rawData);
            if (jsonNode.isArray()) {
                double[] vector = new double[jsonNode.size()];
                for (int i = 0; i < jsonNode.size(); i++) {
                    JsonNode valueNode = jsonNode.get(i);
                    // 兼容数值类型和字符串类型 / Support both numeric and string types
                    if (valueNode.isNumber()) {
                        vector[i] = valueNode.decimalValue().doubleValue();
                    } else if (valueNode.isTextual()) {
                        vector[i] = Double.parseDouble(valueNode.asText());
                    } else {
                        throw new IllegalArgumentException("Vector element must be number or string");
                    }
                }
                return vector;
            } else {
                throw new IllegalArgumentException("Vector data must be an array");
            }
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.VECTOR_PARSE_ERROR,
                    "Failed to parse vector: " + column.asString(), e);
        }
    }

    /**
     * 解析稀疏向量类型（SPARSE_VECTOR）
     * 输入格式：JSON对象字符串，键为字符串，值为Double类型或String类型
     * 例如：{"I": 0.55, "had": 0.4} 或 {"I": "0.55", "had": "0.4"}
     *
     * Parse sparse vector type (SPARSE_VECTOR)
     * Input format: JSON object string, key as string, value as Double or String type
     * Example: {"I": 0.55, "had": 0.4} or {"I": "0.55", "had": "0.4"}
     *
     * 支持数值类型和字符串类型值的自动转换
     * Support automatic conversion of numeric and string type values
     *
     * @param column DataX Column对象 / DataX Column object
     * @return Map<String, Double> 稀疏向量的键值对映射 / Sparse vector key-value mapping
     */
    private static Map<String, Double> parseSparseVector(Column column) {
        try {
            String rawData = column.asString();
            // 使用VectorJson的ObjectMapper，确保浮点数解析精度 / Use VectorJson's ObjectMapper to ensure precision
            JsonNode jsonNode = VectorJson.getMapper().readTree(rawData);
            if (jsonNode.isObject()) {
                Map<String, Double> sparseVector = new HashMap<>();
                jsonNode.fields().forEachRemaining(entry -> {
                    JsonNode valueNode = entry.getValue();
                    // 兼容数值类型和字符串类型 / Support both numeric and string types
                    if (valueNode.isNumber()) {
                        sparseVector.put(entry.getKey(),
                                valueNode.decimalValue().doubleValue());
                    } else if (valueNode.isTextual()) {
                        sparseVector.put(entry.getKey(),
                                Double.parseDouble(valueNode.asText()));
                    } else {
                        throw new IllegalArgumentException("Sparse vector value must be number or string");
                    }
                });
                return sparseVector;
            } else {
                throw new IllegalArgumentException("Sparse vector data must be an object");
            }
        } catch (Exception e) {
            throw DataXException.asDataXException(Elastic8xWriterErrorCode.VECTOR_PARSE_ERROR,
                    "Failed to parse sparse vector: " + column.asString(), e);
        }
    }
}

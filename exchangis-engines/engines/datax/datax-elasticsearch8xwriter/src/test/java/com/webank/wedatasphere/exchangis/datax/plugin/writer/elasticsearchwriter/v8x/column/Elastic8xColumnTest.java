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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Elastic8xColumn单元测试类
 *
 * 测试目标 / Test objectives:
 * - 测试各种数据类型转换（STRING、LONG、DOUBLE、DATE、BOOLEAN等）/ Test various data type conversions
 * - 测试嵌套对象映射 / Test nested object mapping
 * - 测试向量类型（dense_vector）解析 / Test vector type (dense_vector) parsing
 * - 测试自动类型推测（autoDetectFieldType）/ Test auto type detection
 * - 测试边界情况和异常处理 / Test edge cases and exception handling
 *
 * @author davidhua
 * @since 2024-03-13
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Elastic8xColumn单元测试 / Elastic8xColumn Unit Tests")
public class Elastic8xColumnTest {

    @Mock
    private Record mockRecord;

    @Mock
    private Column mockColumn;

    private List<Elastic8xColumn> columnConfigs;
    private static final String COLUMN_SEPARATOR = "\\.";

    /**
     * 初始化测试环境 / Setup test environment
     */
    @BeforeEach
    void setUp() {
        columnConfigs = new ArrayList<>();
    }

    // ==================== 正常场景测试 / Normal Scenario Tests ====================

    @Test
    @DisplayName("应该成功转换STRING类型字段 / Should successfully convert STRING field")
    void should_ConvertStringField_When_ValidStringColumn() {
        // Given / 给定
        setupColumnConfig("username", "text", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, "john_doe");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("username", "john_doe");
    }

    @Test
    @DisplayName("应该成功转换LONG类型字段 / Should successfully convert LONG field")
    void should_ConvertLongField_When_ValidLongColumn() {
        // Given / 给定
        setupColumnConfig("user_id", "long", null, null);
        setupMockColumn(mockColumn, Column.Type.LONG, 123456L);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("user_id", 123456L);
    }

    @Test
    @DisplayName("应该成功转换DOUBLE类型字段 / Should successfully convert DOUBLE field")
    void should_ConvertDoubleField_When_ValidDoubleColumn() {
        // Given / 给定
        setupColumnConfig("price", "double", null, null);
        setupMockColumn(mockColumn, Column.Type.DOUBLE, 99.99);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("price", 99.99);
    }

    @Test
    @DisplayName("应该成功转换BOOLEAN类型字段 / Should successfully convert BOOLEAN field")
    void should_ConvertBooleanField_When_ValidBooleanColumn() {
        // Given / 给定
        setupColumnConfig("is_active", "boolean", null, null);
        setupMockColumn(mockColumn, Column.Type.BOOLEAN, true);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("is_active", true);
    }

    @Test
    @DisplayName("应该成功转换INTEGER类型字段为BigInteger / Should successfully convert INTEGER field to BigInteger")
    void should_ConvertIntegerField_When_ValidIntegerColumn() {
        // Given / 给定
        setupColumnConfig("age", "integer", null, null);
        setupMockColumn(mockColumn, Column.Type.INT, 25);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);
        Object value = result.get("age");
        assertThat(value).isInstanceOf(BigInteger.class);
        assertThat(((BigInteger) value).intValue()).isEqualTo(25);
    }

    @Test
    @DisplayName("应该成功转换BINARY类型字段 / Should successfully convert BINARY field")
    void should_ConvertBinaryField_When_ValidBinaryColumn() {
        // Given / 给定
        byte[] testBytes = "test data".getBytes();
        setupColumnConfig("binary_data", "binary", null, null);
        setupMockColumn(mockColumn, Column.Type.BYTES, testBytes);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("binary_data", testBytes);
    }

    @Test
    @DisplayName("应该成功转换BYTE类型字段为整数 / Should successfully convert BYTE field to integer")
    void should_ConvertByteField_When_ValidByteColumn() {
        // Given / 给定
        // ES的byte类型是整数类型，范围-128到127
        setupColumnConfig("small_number", "byte", null, null);
        setupMockColumn(mockColumn, Column.Type.LONG, 100L);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("small_number", 100L);
    }

    // ==================== 向量类型测试 / Vector Type Tests ====================

    @Test
    @DisplayName("应该成功解析DENSE_VECTOR类型字段 / Should successfully parse DENSE_VECTOR field")
    void should_ParseDenseVector_When_ValidJsonArray() {
        // Given / 给定
        String vectorJson = "[0.1, 0.2, 0.3, 0.4, 0.5]";
        setupColumnConfig("embedding", "dense_vector", null, 5);
        setupMockColumn(mockColumn, Column.Type.STRING, vectorJson);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object embedding = result.get("embedding");
        assertThat(embedding).isInstanceOf(double[].class);

        double[] vector = (double[]) embedding;
        assertThat(vector).hasSize(5);
        assertThat(vector).containsExactly(0.1, 0.2, 0.3, 0.4, 0.5);
    }

    @Test
    @DisplayName("应该抛出异常当向量数据不是JSON数组时 / Should throw exception when vector data is not JSON array")
    void should_ThrowException_When_VectorDataIsNotJsonArray() {
        // Given / 给定
        String invalidVector = "not an array";
        setupColumnConfig("embedding", "dense_vector", null, 3);
        setupMockColumn(mockColumn, Column.Type.STRING, invalidVector);
        setupMockRecord(1);

        // When & Then / 当&那么
        assertThatThrownBy(() ->
                Elastic8xColumn.toData(mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false)
        )
                .isInstanceOf(DataXException.class)
                .hasMessageContaining("Failed to parse vector");
    }

    // ==================== 嵌套对象测试 / Nested Object Tests ====================

    @Test
    @DisplayName("应该成功转换嵌套对象字段 / Should successfully convert nested object field")
    void should_ConvertNestedObject_When_ValidNestedColumn() {
        // Given / 给定
        setupColumnConfig("user.address", "object", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, "{\"city\":\"Beijing\",\"street\":\"Chaoyang\"}");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object userObj = result.get("user");
        assertThat(userObj).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) userObj;
        assertThat(userMap)
                .containsKey("address");

        @SuppressWarnings("unchecked")
        Map<String, Object> addressMap = (Map<String, Object>) userMap.get("address");
        assertThat(addressMap)
                .containsEntry("city", "Beijing")
                .containsEntry("street", "Chaoyang");
    }

    @Test
    @DisplayName("应该成功转换多层嵌套对象字段 / Should successfully convert multi-level nested object field")
    void should_ConvertMultiLevelNestedObject_When_ValidNestedColumn() {
        // Given / 给定
        setupColumnConfig("user.profile.settings", "object", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, "{\"theme\":\"dark\",\"language\":\"zh\"}");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        @SuppressWarnings("unchecked")
        Map<String, Object> userMap = (Map<String, Object>) result.get("user");
        @SuppressWarnings("unchecked")
        Map<String, Object> profileMap = (Map<String, Object>) userMap.get("profile");
        @SuppressWarnings("unchecked")
        Map<String, Object> settingsMap = (Map<String, Object>) profileMap.get("settings");

        assertThat(settingsMap)
                .containsEntry("theme", "dark")
                .containsEntry("language", "zh");
    }

    @Test
    @DisplayName("应该成功转换NESTED数组类型字段 / Should successfully convert NESTED array field")
    void should_ConvertNestedArray_When_ValidJsonArray() {
        // Given / 给定
        String jsonArray = "[{\"name\":\"item1\"},{\"name\":\"item2\"}]";
        setupColumnConfig("items", "nested", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, jsonArray);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object items = result.get("items");
        assertThat(items).isInstanceOf(List.class);
    }

    // ==================== 自动类型推测测试 / Auto Type Detection Tests ====================

    @Test
    @DisplayName("应该自动推测TEXT类型当配置类型为空时 / Should auto-detect TEXT type when config type is empty")
    void should_AutoDetectTextType_When_ConfigTypeIsEmptyAndAllowIndexNotExist() {
        // Given / 给定
        setupColumnConfig("description", null, null, null); // type为空
        setupMockColumn(mockColumn, Column.Type.STRING, "auto detected text");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, true); // allowIndexNotExist=true

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("description", "auto detected text");
    }

    @Test
    @DisplayName("应该自动推测LONG类型当Column类型为LONG时 / Should auto-detect LONG type when Column type is LONG")
    void should_AutoDetectLongType_When_ColumnTypeIsLong() {
        // Given / 给定
        setupColumnConfig("count", null, null, null); // type为空
        setupMockColumn(mockColumn, Column.Type.LONG, 999L);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, true);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("count", 999L);
    }

    @Test
    @DisplayName("应该自动推测BOOLEAN类型当Column类型为BOOLEAN时 / Should auto-detect BOOLEAN type when Column type is BOOLEAN")
    void should_AutoDetectBooleanType_When_ColumnTypeIsBoolean() {
        // Given / 给定
        setupColumnConfig("enabled", null, null, null); // type为空
        setupMockColumn(mockColumn, Column.Type.BOOLEAN, false);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, true);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("enabled", false);
    }

    @Test
    @DisplayName("应该使用TEXT作为默认类型当无法推测时 / Should use TEXT as default when cannot detect type")
    void should_UseTextAsDefault_When_CannotDetectType() {
        // Given / 给定
        setupColumnConfig("unknown_field", null, null, null);
        // 模拟一个未知类型的Column
        when(mockColumn.getType()).thenReturn(null);
        when(mockColumn.asString()).thenReturn("default text");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, true);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("unknown_field", "default text");
    }

    // ==================== 边界值测试 / Edge Case Tests ====================

    @Test
    @DisplayName("应该处理空字符串值 / Should handle empty string value")
    void should_HandleEmptyStringValue_When_ColumnIsEmpty() {
        // Given / 给定
        setupColumnConfig("empty_field", "text", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, "");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("empty_field", "");
    }

    @Test
    @DisplayName("应该处理null值当使用配置类型时 / Should handle null value when using configured type")
    void should_HandleNullValue_When_UsingConfiguredType() {
        // Given / 给定
        setupColumnConfig("nullable_field", "text", null, null);
        when(mockColumn.asString()).thenReturn(null);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("nullable_field", null);
    }

    @Test
    @DisplayName("应该处理多个字段转换 / Should handle multiple field conversions")
    void should_ConvertMultipleFields_When_RecordHasMultipleColumns() {
        // Given / 给定
        setupColumnConfig("id", "long", null, null);
        setupColumnConfig("name", "text", null, null);
        setupColumnConfig("price", "double", null, null);

        // When / 当
        when(mockRecord.getColumnNumber()).thenReturn(3);

        Column idColumn = mock(Column.class);
        when(idColumn.getType()).thenReturn(Column.Type.LONG);
        when(idColumn.asLong()).thenReturn(1L);
        when(mockRecord.getColumn(0)).thenReturn(idColumn);

        Column nameColumn = mock(Column.class);
        when(nameColumn.getType()).thenReturn(Column.Type.STRING);
        when(nameColumn.asString()).thenReturn("Product A");
        when(mockRecord.getColumn(1)).thenReturn(nameColumn);

        Column priceColumn = mock(Column.class);
        when(priceColumn.getType()).thenReturn(Column.Type.DOUBLE);
        when(priceColumn.asDouble()).thenReturn(29.99);
        when(mockRecord.getColumn(2)).thenReturn(priceColumn);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(3)
                .containsEntry("id", 1L)
                .containsEntry("name", "Product A")
                .containsEntry("price", 29.99);
    }

    // ==================== 异常场景测试 / Exception Scenario Tests ====================

    @Test
    @DisplayName("应该使用TEXT作为默认类型当配置类型未知时 / Should use TEXT as default when config type is unknown")
    void should_UseTextAsDefault_When_ConfigTypeIsUnknown() {
        // Given / 给定
        setupColumnConfig("unknown_type_field", "unknown_xyz_type", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, "test value");
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1)
                .containsEntry("unknown_type_field", "test value");
    }

    // ==================== 辅助方法 / Helper Methods ====================

    /**
     * 设置字段配置 / Setup column configuration
     *
     * @param name 字段名 / Field name
     * @param type 字段类型 / Field type
     * @param format 格式 / Format
     * @param dims 向量维度 / Vector dimensions
     */
    private void setupColumnConfig(String name, String type, String format, Integer dims) {
        Elastic8xColumn config = new Elastic8xColumn();
        config.setName(name);
        config.setType(type);
        config.setFormat(format);
        config.setDims(dims);
        columnConfigs.add(config);
    }

    /**
     * 设置Mock Column / Setup mock column
     *
     * @param column Mock Column对象 / Mock column object
     * @param type Column类型 / Column type
     * @param value 字段值 / Field value
     */
    private void setupMockColumn(Column column, Column.Type type, Object value) {
        when(column.getType()).thenReturn(type);

        if (type == Column.Type.STRING) {
            when(column.asString()).thenReturn((String) value);
        } else if (type == Column.Type.LONG) {
            when(column.asLong()).thenReturn((Long) value);
        } else if (type == Column.Type.DOUBLE) {
            when(column.asDouble()).thenReturn((Double) value);
        } else if (type == Column.Type.BOOLEAN) {
            when(column.asBoolean()).thenReturn((Boolean) value);
        } else if (type == Column.Type.INT) {
            when(column.asBigInteger()).thenReturn(BigInteger.valueOf((Integer) value));
        } else if (type == Column.Type.BYTES) {
            when(column.asBytes()).thenReturn((byte[]) value);
        }
    }

    /**
     * 设置Mock Record / Setup mock record
     *
     * @param columnCount 列数量 / Column count
     */
    private void setupMockRecord(int columnCount) {
        when(mockRecord.getColumnNumber()).thenReturn(columnCount);
        for (int i = 0; i < columnCount; i++) {
            when(mockRecord.getColumn(i)).thenReturn(mockColumn);
        }
    }

    // ==================== 精度测试 / Precision Tests ====================

    @Test
    @DisplayName("应该保持浮点数精度解析SPARSE_VECTOR / Should preserve floating-point precision when parsing SPARSE_VECTOR")
    void should_PreserveFloatPrecision_When_ParsingSparseVector() {
        // Given / 给定
        // 使用容易产生精度问题的浮点数 / Use floating-point numbers that are prone to precision issues
        String sparseVectorJson = "{\"value1\": 0.1, \"value2\": 0.2, \"value3\": 0.3}";
        setupColumnConfig("sparse_embedding", "sparse_vector", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, sparseVectorJson);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object sparseEmbedding = result.get("sparse_embedding");
        assertThat(sparseEmbedding).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Double> sparseVector = (Map<String, Double>) sparseEmbedding;

        // 验证精度：使用decimalValue()直接获取BigDecimal应该保证精度
        // Verify precision: using decimalValue() to directly get BigDecimal should preserve precision
        assertThat(sparseVector.get("value1")).isEqualTo(0.1);
        assertThat(sparseVector.get("value2")).isEqualTo(0.2);
        assertThat(sparseVector.get("value3")).isEqualTo(0.3);
    }

    @Test
    @DisplayName("应该保持浮点数精度解析DENSE_VECTOR / Should preserve floating-point precision when parsing DENSE_VECTOR")
    void should_PreserveFloatPrecision_When_ParsingDenseVector() {
        // Given / 给定
        // 使用容易产生精度问题的浮点数 / Use floating-point numbers that are prone to precision issues
        String vectorJson = "[0.1, 0.2, 0.3, 0.123456789]";
        setupColumnConfig("embedding", "dense_vector", null, 4);
        setupMockColumn(mockColumn, Column.Type.STRING, vectorJson);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object embedding = result.get("embedding");
        assertThat(embedding).isInstanceOf(double[].class);

        double[] vector = (double[]) embedding;

        // 验证精度：使用BigDecimal作为中间层应该保证精度
        // Verify precision: using BigDecimal as intermediate should preserve precision
        assertThat(vector[0]).isEqualTo(0.1, within(0.0001));
        assertThat(vector[1]).isEqualTo(0.2, within(0.0001));
        assertThat(vector[2]).isEqualTo(0.3, within(0.0001));
        assertThat(vector[3]).isEqualTo(0.123456789, within(0.000000001));
    }

    @Test
    @DisplayName("应该成功解析SPARSE_VECTOR类型字段 / Should successfully parse SPARSE_VECTOR field")
    void should_ParseSparseVector_When_ValidJsonObject() {
        // Given / 给定
        String sparseVectorJson = "{\"I\": 0.55, \"had\": 0.4, \"the\": 0.3}";
        setupColumnConfig("text_embedding", "sparse_vector", null, null);
        setupMockColumn(mockColumn, Column.Type.STRING, sparseVectorJson);
        setupMockRecord(1);

        // When / 当
        Map<String, Object> result = Elastic8xColumn.toData(
                mockRecord, columnConfigs, COLUMN_SEPARATOR, null, false);

        // Then / 那么
        assertThat(result)
                .isNotNull()
                .hasSize(1);

        Object textEmbedding = result.get("text_embedding");
        assertThat(textEmbedding).isInstanceOf(Map.class);

        @SuppressWarnings("unchecked")
        Map<String, Double> sparseVector = (Map<String, Double>) textEmbedding;
        assertThat(sparseVector)
                .hasSize(3)
                .containsEntry("I", 0.55)
                .containsEntry("had", 0.4)
                .containsEntry("the", 0.3);
    }
}

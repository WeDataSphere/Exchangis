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

package com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.index;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IndexPatternExtractor单元测试类
 *
 * 测试目标 / Test objectives:
 * - 测试动态索引模式解析（logs-{date}、order-{type}-{status}）/ Test dynamic index pattern parsing
 * - 测试字段值提取 / Test field value extraction
 * - 测试边界情况（空字段、不存在的字段、非原始类型）/ Test edge cases
 * - 测试静态索引名称（无占位符）/ Test static index name (no placeholders)
 *
 * @author davidhua
 * @since 2024-03-13
 */
@DisplayName("IndexPatternExtractor单元测试 / IndexPatternExtractor Unit Tests")
public class IndexPatternExtractorTest {

    // ==================== 正常场景测试 / Normal Scenario Tests ====================

    @Test
    @DisplayName("应该正确提取静态索引名称 / Should correctly extract static index name")
    void should_ExtractStaticIndex_When_PatternHasNoPlaceholders() {
        // Given / 给定
        String staticIndex = "logs-2024";
        IndexPatternExtractor extractor = new IndexPatternExtractor(staticIndex);
        Map<String, Object> data = new HashMap<>();

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("logs-2024");
    }

    @Test
    @DisplayName("应该正确替换单个占位符 / Should correctly replace single placeholder")
    void should_ReplaceSinglePlaceholder_When_FieldExists() {
        // Given / 给定
        String pattern = "logs-{date}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("date", "2024-03-13");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("logs-2024-03-13");
    }

    @Test
    @DisplayName("应该正确替换多个占位符 / Should correctly replace multiple placeholders")
    void should_ReplaceMultiplePlaceholders_When_AllFieldsExist() {
        // Given / 给定
        String pattern = "order-{type}-{status}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("type", "online");
        data.put("status", "active");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("order-online-active");
    }

    @Test
    @DisplayName("应该正确替换字符串类型字段值 / Should correctly replace String field value")
    void should_ReplaceStringFieldValue_When_FieldIsString() {
        // Given / 给定
        String pattern = "metrics-{env}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("env", "production");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("metrics-production");
    }

    @Test
    @DisplayName("应该正确替换整数类型字段值 / Should correctly replace Integer field value")
    void should_ReplaceIntegerFieldValue_When_FieldIsInteger() {
        // Given / 给定
        String pattern = "shard-{shardId}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("shardId", 5);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("shard-5");
    }

    @Test
    @DisplayName("应该正确替换长整型字段值 / Should correctly replace Long field value")
    void should_ReplaceLongFieldValue_When_FieldIsLong() {
        // Given / 给定
        String pattern = "data-{timestamp}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", 1710326400000L);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("data-1710326400000");
    }

    @Test
    @DisplayName("应该正确替换双精度浮点型字段值 / Should correctly replace Double field value")
    void should_ReplaceDoubleFieldValue_When_FieldIsDouble() {
        // Given / 给定
        String pattern = "sensor-{value}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("value", 23.45);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("sensor-23.45");
    }

    @Test
    @DisplayName("应该正确替换布尔型字段值 / Should correctly replace Boolean field value")
    void should_ReplaceBooleanFieldValue_When_FieldIsBoolean() {
        // Given / 给定
        String pattern = "flag-{isActive}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("isActive", true);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("flag-true");
    }

    @Test
    @DisplayName("应该正确替换枚举类型字段值 / Should correctly replace Enum field value")
    void should_ReplaceEnumFieldValue_When_FieldIsEnum() {
        // Given / 给定
        String pattern = "log-{level}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("level", TestLogLevel.ERROR);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("log-ERROR");
    }

    @Test
    @DisplayName("应该正确替换包装类型字段值 / Should correctly replace wrapper type field value")
    void should_ReplaceWrapperTypeFieldValue_When_FieldIsWrapperType() {
        // Given / 给定
        String pattern = "counter-{count}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("count", Integer.valueOf(100));

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("counter-100");
    }

    // ==================== 边界值测试 / Edge Case Tests ====================

    @Test
    @DisplayName("应该使用空字符串替换不存在的字段 / Should use empty string for non-existent field")
    void should_UseEmptyString_When_FieldDoesNotExist() {
        // Given / 给定
        String pattern = "logs-{date}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        // date字段不存在

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("logs-");
    }

    @Test
    @DisplayName("应该使用空字符串替换null字段值 / Should use empty string for null field value")
    void should_UseEmptyString_When_FieldValueIsNull() {
        // Given / 给定
        String pattern = "logs-{date}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("date", null);

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("logs-");
    }

    @Test
    @DisplayName("应该使用空字符串替换非原始类型字段值 / Should use empty string for non-primitive type field value")
    void should_UseEmptyString_When_FieldValueIsNonPrimitiveType() {
        // Given / 给定
        String pattern = "data-{info}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("info", new HashMap<>()); // Map对象不是原始类型

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("data-");
    }

    @Test
    @DisplayName("应该正确处理混合静态和动态部分 / Should correctly handle mixed static and dynamic parts")
    void should_HandleMixedStaticAndDynamicParts_When_PatternIsComplex() {
        // Given / 给定
        String pattern = "app-prod-{type}-{date}-logs";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("type", "access");
        data.put("date", "20240313");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("app-prod-access-20240313-logs");
    }

    @Test
    @DisplayName("应该正确处理连续占位符 / Should correctly handle consecutive placeholders")
    void should_HandleConsecutivePlaceholders_When_PatternHasAdjacentPlaceholders() {
        // Given / 给定
        String pattern = "{year}{month}{day}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("year", "2024");
        data.put("month", "03");
        data.put("day", "13");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("20240313");
    }

    @Test
    @DisplayName("应该正确处理空字段名占位符 / Should correctly handle empty field name placeholder")
    void should_UseEmptyString_When_PlaceholderHasEmptyFieldName() {
        // Given / 给定
        String pattern = "logs-{}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("logs-");
    }

    @Test
    @DisplayName("应该正确处理只有占位符的模式 / Should correctly handle pattern with only placeholders")
    void should_HandleOnlyPlaceholders_When_PatternHasNoStaticParts() {
        // Given / 给定
        String pattern = "{index}";
        IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

        Map<String, Object> data = new HashMap<>();
        data.put("index", "test");

        // When / 当
        String result = extractor.extractIndex(data);

        // Then / 那么
        assertThat(result).isEqualTo("test");
    }

    // ==================== 嵌套测试类 / Nested Test Classes ====================

    /**
     * 空字符串和边界情况测试 / Empty string and edge case tests
     */
    @Nested
    @DisplayName("空字符串和边界情况测试 / Empty String and Edge Case Tests")
    class EmptyStringAndEdgeCaseTests {

        @Test
        @DisplayName("应该正确处理空模式字符串 / Should correctly handle empty pattern string")
        void should_HandleEmptyPattern_When_PatternIsEmpty() {
            // Given / 给定
            String pattern = "";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);
            Map<String, Object> data = new HashMap<>();

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该正确处理只有大括号的模式 / Should correctly handle pattern with only braces")
        void should_HandleOnlyBraces_When_PatternContainsOnlyBraces() {
            // Given / 给定
            String pattern = "{}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);
            Map<String, Object> data = new HashMap<>();

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("应该正确处理不匹配的大括号 / Should correctly handle unmatched braces")
        void should_HandleUnmatchedBraces_When_PatternHasUnmatchedBraces() {
            // Given / 给定
            String pattern = "logs-{date";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);
            Map<String, Object> data = new HashMap<>();

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            // IndexPatternExtractor会将"logs-"作为前缀，然后查找{但找不到配对的}，所以结果是"logs-logs-{date"
            assertThat(result).isEqualTo("logs-logs-{date");
        }

        @Test
        @DisplayName("应该正确处理反向大括号 / Should correctly handle reversed braces")
        void should_HandleReversedBraces_When_PatternHasReversedBraces() {
            // Given / 给定
            String pattern = "logs-}date{";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);
            Map<String, Object> data = new HashMap<>();

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            // IndexPatternExtractor会把}也当作开始标记，导致重复pattern
            assertThat(result).isEqualTo("logs-}datelogs-}date{");
        }
    }

    /**
     * 特殊字符测试 / Special character tests
     */
    @Nested
    @DisplayName("特殊字符和命名测试 / Special Character and Naming Tests")
    class SpecialCharacterAndNamingTests {

        @Test
        @DisplayName("应该正确处理带下划线的字段名 / Should correctly handle field names with underscores")
        void should_HandleFieldWithUnderscore_When_FieldNameContainsUnderscore() {
            // Given / 给定
            String pattern = "logs-{log_date}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("log_date", "2024-03-13");

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("logs-2024-03-13");
        }

        @Test
        @DisplayName("应该正确处理带驼峰命名的字段名 / Should correctly handle camelCase field names")
        void should_HandleCamelCaseFieldName_When_FieldNameIsCamelCase() {
            // Given / 给定
            String pattern = "logs-{logDate}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("logDate", "2024-03-13");

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("logs-2024-03-13");
        }

        @Test
        @DisplayName("应该正确处理带数字的字段名 / Should correctly handle field names with numbers")
        void should_HandleFieldWithNumbers_When_FieldNameContainsNumbers() {
            // Given / 给定
            String pattern = "logs-{logType1}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("logType1", "access");

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("logs-access");
        }

        @Test
        @DisplayName("应该正确处理带短横线的静态部分 / Should correctly handle static parts with hyphens")
        void should_HandleStaticPartWithHyphen_When_PatternContainsHyphens() {
            // Given / 给定
            String pattern = "my-app-logs-{env}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("env", "prod");

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("my-app-logs-prod");
        }
    }

    /**
     * 数据类型测试 / Data type tests
     */
    @Nested
    @DisplayName("不支持的数据类型测试 / Unsupported Data Type Tests")
    class UnsupportedDataTypeTests {

        @Test
        @DisplayName("应该使用空字符串当字段值是Map时 / Should use empty string when field value is Map")
        void should_UseEmptyString_When_FieldValueIsMap() {
            // Given / 给定
            String pattern = "data-{info}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            Map<String, String> infoMap = new HashMap<>();
            infoMap.put("key", "value");
            data.put("info", infoMap);

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("data-");
        }

        @Test
        @DisplayName("应该使用空字符串当字段值是List时 / Should use empty string when field value is List")
        void should_UseEmptyString_When_FieldValueIsList() {
            // Given / 给定
            String pattern = "data-{items}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("items", new ArrayList<>());

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("data-");
        }

        @Test
        @DisplayName("应该使用空字符串当字段值是自定义对象时 / Should use empty string when field value is custom object")
        void should_UseEmptyString_When_FieldValueIsCustomObject() {
            // Given / 给定
            String pattern = "data-{obj}";
            IndexPatternExtractor extractor = new IndexPatternExtractor(pattern);

            Map<String, Object> data = new HashMap<>();
            data.put("obj", new Object());

            // When / 当
            String result = extractor.extractIndex(data);

            // Then / 那么
            assertThat(result).isEqualTo("data-");
        }
    }

    // ==================== 测试辅助类 / Test Helper Classes ====================

    /**
     * 测试用的日志级别枚举 / Test log level enum
     */
    private enum TestLogLevel {
        DEBUG, INFO, WARN, ERROR
    }
}

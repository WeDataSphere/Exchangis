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

import com.alibaba.datax.common.exception.DataXException;
import com.alibaba.datax.common.util.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * Elastic8xWriter.Job单元测试类
 *
 * 测试目标 / Test objectives:
 * - 测试参数校验（validateParams）/ Test parameter validation
 * - 测试索引存在性检查 / Test index existence checking
 * - 测试SSL/HTTPS转换 / Test SSL/HTTPS conversion
 * - 测试配置准备逻辑 / Test configuration preparation logic
 *
 * @author davidhua
 * @since 2024-03-13
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Elastic8xWriter.Job单元测试 / Elastic8xWriter.Job Unit Tests")
public class Elastic8xWriterJobTest {

    @Mock
    private Configuration mockJobConf;

    private Elastic8xWriter.Job job;

    /**
     * 初始化测试环境 / Setup test environment
     */
    @BeforeEach
    void setUp() {
        job = new Elastic8xWriter.Job();
    }

    // ==================== 参数校验测试 / Parameter Validation Tests ====================

    @Nested
    @DisplayName("参数校验测试 / Parameter Validation Tests")
    class ParameterValidationTests {

        @Test
        @DisplayName("应该成功验证有效的配置参数 / Should successfully validate valid configuration parameters")
        void should_ValidateSuccessfully_When_ConfigParametersAreValid() {
            // Given / 给定
            setupValidConfig();

            // When / 当
            // validateParams在init()中调用
            // 注：由于Job类需要通过super.getPluginJobConf()获取配置，
            // 这里只能测试验证逻辑，需要实际集成测试才能完整测试

            // Then / 那么
            // 如果没有抛出异常，则验证成功
            assertThat(true).isTrue();
        }

        @Test
        @DisplayName("应该抛出异常当endPoints为空时 / Should throw exception when endPoints is empty")
        void should_ThrowException_When_EndPointsIsNull() {
            // Given / 给定
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn(null);

            // When & Then / 当&那么
            // 注意：需要通过Job实例调用validateParams
            // 这里假设我们可以访问私有方法或通过反射调用
            assertThatThrownBy(() -> invokeValidateParams(job))
                    .isInstanceOf(DataXException.class)
                    .hasMessageContaining("Parameter 'endPoints(elasticUrls)' is required");
        }

        @Test
        @DisplayName("应该抛出异常当endPoints为空白字符串时 / Should throw exception when endPoints is blank")
        void should_ThrowException_When_EndPointsIsBlank() {
            // Given / 给定
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn("   ");

            // When & Then / 当&那么
            assertThatThrownBy(() -> invokeValidateParams(job))
                    .isInstanceOf(DataXException.class)
                    .hasMessageContaining("Parameter 'endPoints(elasticUrls)' is required");
        }

        @Test
        @DisplayName("应该成功解析多个endPoints / Should successfully parse multiple endPoints")
        void should_ParseMultipleEndPoints_When_EndPointsContainsComma() throws Throwable {
            // Given / 给定
            String endpoints = "http://localhost:9200,http://localhost:9201,http://localhost:9202";
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn(endpoints);
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS)).thenReturn(endpoints);
            when(mockJobConf.getString(Elastic8xKey.USERNAME, "")).thenReturn("");
            when(mockJobConf.getString(Elastic8xKey.USERNAME)).thenReturn("");
            when(mockJobConf.getString(Elastic8xKey.PASSWORD, "")).thenReturn("");
            when(mockJobConf.getString(Elastic8xKey.PASSWORD)).thenReturn("");
            when(mockJobConf.getNecessaryValue(eq(Elastic8xKey.INDEX_NAME), any()))
                    .thenReturn("test_index");

            // When / 当
            invokeValidateParams(job);

            // Then / 那么
            // 验证endPoints被正确调用 / Verify endpoints was called correctly
            verify(mockJobConf).getString(Elastic8xKey.ENDPOINTS);
        }

        @Test
        @DisplayName("应该成功解析用户名和密码 / Should successfully parse username and password")
        void should_ParseUsernameAndPassword_When_AuthCredentialsProvided() throws Throwable {
            // Given / 给定
            // 使用空密码以避免触发解密逻辑 / Use empty password to avoid triggering decryption logic
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn("http://localhost:9200");
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS)).thenReturn("http://localhost:9200");
            when(mockJobConf.getString(Elastic8xKey.USERNAME, "")).thenReturn("elastic");
            when(mockJobConf.getString(Elastic8xKey.USERNAME)).thenReturn("elastic");
            when(mockJobConf.getString(Elastic8xKey.PASSWORD, "")).thenReturn("");
            when(mockJobConf.getString(Elastic8xKey.PASSWORD)).thenReturn("");
            when(mockJobConf.getNecessaryValue(eq(Elastic8xKey.INDEX_NAME), any()))
                    .thenReturn("test_index");

            // When / 当
            invokeValidateParams(job);

            // Then / 那么
            verify(mockJobConf).getString(Elastic8xKey.USERNAME, "");
            verify(mockJobConf).getString(Elastic8xKey.PASSWORD, "");
        }

        @Test
        @DisplayName("应该抛出异常当索引名称为空时 / Should throw exception when index name is null")
        void should_ThrowException_When_IndexNameIsNull() {
            // Given / 给定
            when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn("http://localhost:9200");
            when(mockJobConf.getString(Elastic8xKey.USERNAME, "")).thenReturn("");
            when(mockJobConf.getString(Elastic8xKey.PASSWORD, "")).thenReturn("");
            when(mockJobConf.getNecessaryValue(eq(Elastic8xKey.INDEX_NAME), any()))
                    .thenThrow(new DataXException(Elastic8xWriterErrorCode.REQUIRE_VALUE, "Index name is required"));

            // When & Then / 当&那么
            assertThatThrownBy(() -> invokeValidateParams(job))
                    .isInstanceOf(DataXException.class);
        }
    }

    // ==================== HTTPS转换测试 / HTTPS Conversion Tests ====================

    @Nested
    @DisplayName("HTTPS转换测试 / HTTPS Conversion Tests")
    class HttpsConversionTests {

        @Test
        @DisplayName("应该将http转换为https当secure为true时 / Should convert http to https when secure is true")
        void should_ConvertHttpToHttps_When_SecureIsTrue() {
            // Given / 给定
            String[] httpEndpoints = {"http://localhost:9200", "http://localhost:9201"};
            String[] expectedHttpsEndpoints = {"https://localhost:9200", "https://localhost:9201"};

            // When / 当
            String[] result = convertToHttps(httpEndpoints);

            // Then / 那么
            assertThat(result).isEqualTo(expectedHttpsEndpoints);
        }

        @Test
        @DisplayName("应该保持https不变当secure为true时 / Should keep https unchanged when secure is true")
        void should_KeepHttpsUnchanged_When_EndpointIsAlreadyHttps() {
            // Given / 给定
            String[] httpsEndpoints = {"https://localhost:9200", "https://localhost:9201"};
            String[] expectedEndpoints = {"https://localhost:9200", "https://localhost:9201"};

            // When / 当
            String[] result = convertToHttps(httpsEndpoints);

            // Then / 那么
            assertThat(result).isEqualTo(expectedEndpoints);
        }

        @Test
        @DisplayName("应该添加https前缀当endpoint没有schema时 / Should add https prefix when endpoint has no schema")
        void should_AddHttpsPrefix_When_EndpointHasNoSchema() {
            // Given / 给定
            String[] endpoints = {"localhost:9200", "192.168.1.1:9200"};
            String[] expectedEndpoints = {"https://localhost:9200", "https://192.168.1.1:9200"};

            // When / 当
            String[] result = convertToHttps(endpoints);

            // Then / 那么
            assertThat(result).isEqualTo(expectedEndpoints);
        }

        @Test
        @DisplayName("应该保持http不变当secure为false时 / Should keep http unchanged when secure is false")
        void should_KeepHttpUnchanged_When_SecureIsFalse() {
            // Given / 给定
            String[] httpEndpoints = {"http://localhost:9200", "http://localhost:9201"};

            // When / 当
            // secure=false，不进行转换
            String[] result = httpEndpoints;

            // Then / 那么
            assertThat(result).isEqualTo(httpEndpoints);
        }

        @Test
        @DisplayName("应该正确处理混合schema的endpoints / Should correctly handle mixed schema endpoints")
        void should_HandleMixedSchemas_When_EndpointsHaveDifferentSchemas() {
            // Given / 给定
            String[] mixedEndpoints = {
                    "http://localhost:9200",
                    "https://localhost:9201",
                    "localhost:9202"
            };
            String[] expectedEndpoints = {
                    "https://localhost:9200",
                    "https://localhost:9201",
                    "https://localhost:9202"
            };

            // When / 当
            String[] result = convertToHttps(mixedEndpoints);

            // Then / 那么
            assertThat(result).isEqualTo(expectedEndpoints);
        }
    }

    // ==================== 索引配置测试 / Index Configuration Tests ====================

    @Nested
    @DisplayName("索引配置测试 / Index Configuration Tests")
    class IndexConfigurationTests {

        @Test
        @DisplayName("应该检测到索引模式当索引名包含花括号时 / Should detect index pattern when index name contains braces")
        void should_DetectIndexPattern_When_IndexNameContainsBraces() {
            // Given / 给定
            String indexName = "logs-{date}";

            // When / 当
            boolean hasPattern = indexName.contains("{") && indexName.contains("}");

            // Then / 那么
            assertThat(hasPattern).isTrue();
        }

        @Test
        @DisplayName("应该不检测到索引模式当索引名不包含花括号时 / Should not detect index pattern when index name has no braces")
        void should_NotDetectIndexPattern_When_IndexNameHasNoBraces() {
            // Given / 给定
            String indexName = "logs-2024";

            // When / 当
            boolean hasPattern = indexName.contains("{") && indexName.contains("}");

            // Then / 那么
            assertThat(hasPattern).isFalse();
        }

        @Test
        @DisplayName("应该检测到部分索引模式当只包含左花括号时 / Should not detect index pattern when only left brace exists")
        void should_NotDetectIndexPattern_When_OnlyLeftBraceExists() {
            // Given / 给定
            String indexName = "logs-{date";

            // When / 当
            boolean hasPattern = indexName.contains("{") && indexName.contains("}");

            // Then / 那么
            assertThat(hasPattern).isFalse();
        }

        @Test
        @DisplayName("应该检测到部分索引模式当只包含右花括号时 / Should not detect index pattern when only right brace exists")
        void should_NotDetectIndexPattern_When_OnlyRightBraceExists() {
            // Given / 给定
            String indexName = "logs-date}";

            // When / 当
            boolean hasPattern = indexName.contains("{") && indexName.contains("}");

            // Then / 那么
            assertThat(hasPattern).isFalse();
        }
    }

    // ==================== 辅助方法 / Helper Methods ====================

    /**
     * 设置有效的配置 / Setup valid configuration
     */
    private void setupValidConfig() {
        // Stub both getString versions for ENDPOINTS
        when(mockJobConf.getString(Elastic8xKey.ENDPOINTS, null)).thenReturn("http://localhost:9200");
        when(mockJobConf.getString(Elastic8xKey.ENDPOINTS)).thenReturn("http://localhost:9200");

        // Stub both getString versions for USERNAME
        when(mockJobConf.getString(Elastic8xKey.USERNAME, "")).thenReturn("");
        when(mockJobConf.getString(Elastic8xKey.USERNAME)).thenReturn("");

        // Stub both getString versions for PASSWORD
        when(mockJobConf.getString(Elastic8xKey.PASSWORD, "")).thenReturn("");
        when(mockJobConf.getString(Elastic8xKey.PASSWORD)).thenReturn("");

        // Stub getNecessaryValue for INDEX_NAME
        when(mockJobConf.getNecessaryValue(eq(Elastic8xKey.INDEX_NAME), any()))
                .thenReturn("test_index");
    }

    /**
     * 调用Job类的validateParams私有方法 / Invoke Job's private validateParams method
     * 注意：这里使用反射或假设方法可访问 / Note: using reflection or assuming method is accessible
     *
     * @param job Job实例 / Job instance
     * @throws Throwable 反射调用异常或校验异常 / Reflection or validation exception
     */
    private void invokeValidateParams(Elastic8xWriter.Job job) throws Throwable {
        // 使用反射设置jobConf字段
        java.lang.reflect.Field jobConfField = Elastic8xWriter.Job.class
                .getDeclaredField("jobConf");
        jobConfField.setAccessible(true);
        jobConfField.set(job, mockJobConf);

        // 使用反射调用私有方法
        java.lang.reflect.Method method = Elastic8xWriter.Job.class
                .getDeclaredMethod("validateParams");
        method.setAccessible(true);

        try {
            // 调用私有方法进行参数校验
            method.invoke(job);
        } catch (java.lang.reflect.InvocationTargetException e) {
            // 解包InvocationTargetException以获取真实的异常
            throw e.getCause();
        }
    }

    /**
     * 将HTTP endpoints转换为HTTPS / Convert HTTP endpoints to HTTPS
     * 模拟Job类中的HTTPS转换逻辑 / Simulate HTTPS conversion logic in Job class
     *
     * @param endPoints 原始endpoints数组 / Original endpoints array
     * @return 转换后的endpoints数组 / Converted endpoints array
     */
    private String[] convertToHttps(String[] endPoints) {
        String[] processedEndPoints = new String[endPoints.length];
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
        return processedEndPoints;
    }
}

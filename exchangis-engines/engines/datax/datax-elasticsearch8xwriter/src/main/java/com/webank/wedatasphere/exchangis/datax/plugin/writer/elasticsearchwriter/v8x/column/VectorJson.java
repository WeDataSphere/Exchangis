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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * VectorJson类 - 专门用于向量解析的JSON工具类
 *
 * 参考Json类设计，但增加了对浮点数精度的支持
 * 通过启用USE_BIG_DECIMAL_FOR_FLOATS配置，确保浮点数解析时不会损失精度
 *
 * 核心特性：
 * 1. 使用BigDecimal作为浮点数的中间表示，避免double->float转换时的精度丢失
 * 2. 提供静态getMapper()方法返回配置好的ObjectMapper实例
 *
 * @author davidhua
 * @since 2025-03-19
 */
public class VectorJson {
    private static final Logger LOG = LoggerFactory.getLogger(VectorJson.class);

    private static ObjectMapper mapper;

    static {
        mapper = new ObjectMapper();

        // 关键配置：使用BigDecimal解析浮点数，避免精度丢失
        // 这确保了在解析JSON中的浮点数时，Jackson会使用BigDecimal而不是double
        // Key configuration: Use BigDecimal to parse floats, avoiding precision loss
        // This ensures Jackson uses BigDecimal instead of double when parsing floating-point numbers in JSON
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);

        // 其他配置（参考Json类） / Other configurations (reference Json class)
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        LOG.info("VectorJson initialized with USE_BIG_DECIMAL_FOR_FLOATS enabled for high precision floating-point parsing / VectorJson已启用USE_BIG_DECIMAL_FOR_FLOATS以支持高精度浮点数解析");
    }

    /**
     * 私有构造函数，防止实例化 / Private constructor to prevent instantiation
     */
    private VectorJson() {
        throw new UnsupportedOperationException("VectorJson is a utility class and cannot be instantiated / VectorJson是工具类，无法实例化");
    }

    /**
     * 获取配置好的ObjectMapper实例 / Get configured ObjectMapper instance
     *
     * @return ObjectMapper实例，启用了USE_BIG_DECIMAL_FOR_FLOATS / ObjectMapper instance with USE_BIG_DECIMAL_FOR_FLOATS enabled
     */
    public static ObjectMapper getMapper() {
        return mapper;
    }
}

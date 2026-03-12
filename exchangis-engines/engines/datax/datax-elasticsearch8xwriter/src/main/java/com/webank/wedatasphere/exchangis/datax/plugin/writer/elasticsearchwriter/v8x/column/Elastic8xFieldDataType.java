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

/**
 * Elastic8x字段数据类型枚举
 *
 * 参考ES6的ElasticFieldDataType设计，支持ES8的新特性（如dense_vector）
 *
 * @author davidhua
 * 2019/8/15
 */
public enum Elastic8xFieldDataType {
    /**
     * type:text
     */
    TEXT,
    /**
     * type:object
     */
    OBJECT,
    /**
     * type:long(numeric)
     */
    LONG,
    /**
     * type:integer(numeric)
     */
    INTEGER,
    /**
     * type:short(numeric)
     */
    SHORT,
    /**
     * type:byte(numeric)
     */
    BYTE,
    /**
     * type:double(numeric)
     */
    DOUBLE,
    /**
     * type:float(numeric)
     */
    FLOAT,
    /**
     * type:half_float(numeric)
     */
    HALF_FLOAT,
    /**
     * type:scaled_float(numeric)
     */
    SCALED_FLOAT,
    /**
     * type:binary
     */
    BINARY,
    /**
     * type:boolean
     */
    BOOLEAN,
    /**
     * type:date
     */
    DATE,
    /**
     * type:keyword
     */
    KEYWORD,
    /**
     * type:nested
     */
    NESTED,
    /**
     * type:dense_vector (ES8新特性)
     */
    DENSE_VECTOR
}

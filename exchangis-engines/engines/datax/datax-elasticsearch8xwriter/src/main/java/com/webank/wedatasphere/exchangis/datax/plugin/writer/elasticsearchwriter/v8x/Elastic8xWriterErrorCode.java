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

import com.alibaba.datax.common.spi.ErrorCode;

/**
 * Elastic8x Writer错误码枚举
 *
 * 参考ES6的ElasticWriterErrorCode设计
 *
 * @author davidhua
 * 2019/8/12
 */
public enum Elastic8xWriterErrorCode implements ErrorCode {
    /**
     * bad connection
     */
    BAD_CONNECT("ES8xWriter-01", "Cannot connect to Elasticsearch 8.x server"),
    CLOSE_EXCEPTION("ES8xWriter-02", "Cannot close the Elasticsearch 8.x client"),
    REQUIRE_VALUE("ES8xWriter-03", "Necessary value"),
    REQUEST_ERROR("ES8xWriter-04", "Send request error"),
    CREATE_INDEX_ERROR("ES8xWriter-05", "Create index error"),
    DELETE_INDEX_ERROR("ES8xWriter-06", "Delete index error"),
    PUT_MAPPINGS_ERROR("ES8xWriter-07", "Put mappings error"),
    MAPPING_TYPE_UNSUPPORTED("ES8xWriter-08", "Unsupported mapping type"),
    BULK_REQ_ERROR("ES8xWriter-09", "Bulk request error"),
    INDEX_NOT_EXIST("ES8xWriter-10", "Index not exist"),
    CONFIG_ERROR("ES8xWriter-11", "Config error"),
    TOO_MANY_DIRTY_DATA("ES8xWriter-12", "Too many dirty data"),
    VECTOR_PARSE_ERROR("ES8xWriter-13", "Vector parse error");

    private final String code;
    private final String description;

    Elastic8xWriterErrorCode(String code, String description){
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDescription() {
        return description;
    }
}

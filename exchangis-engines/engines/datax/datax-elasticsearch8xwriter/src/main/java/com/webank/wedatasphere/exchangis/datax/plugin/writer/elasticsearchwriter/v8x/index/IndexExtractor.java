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

import java.util.Map;

/**
 * 索引名称提取器接口 / Index name extractor interface
 *
 * 核心职责 / Core responsibilities:
 * - 从文档数据中提取动态索引名称 / Extract dynamic index name from document data
 * - 支持索引模式解析（如logs-{date}）/ Support index pattern parsing (e.g., logs-{date})
 * - 为每个文档动态计算目标索引 / Dynamically calculate target index for each document
 *
 * 使用场景 / Usage scenarios:
 * - 按日期分片索引（如logs-2024-03-13）/ Date-based sharded indices (e.g., logs-2024-03-13)
 * - 按业务类型分片索引（如order-{type}）/ Business-type sharded indices (e.g., order-{type})
 * - 按租户分片索引（如data-{tenantId}）/ Tenant-based sharded indices (e.g., data-{tenantId})
 *
 * 实现类 / Implementation classes:
 * - IndexPatternExtractor: 支持占位符模式的索引提取器 / Index extractor with placeholder pattern support
 *
 * @author davidhua
 * @since 2024-03-13
 */
public interface IndexExtractor {

    /**
     * 从列数据中提取索引名称 / Extract index name from column data
     *
     * @param columns 文档数据（列名->列值的映射）/ Document data (column name -> value mapping)
     * @return 索引名称 / Index name
     */
    String extractIndex(Map<String, Object> columns);
}

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

import co.elastic.clients.elasticsearch.core.bulk.BulkOperationVariant;
import co.elastic.clients.elasticsearch.core.bulk.IndexOperation;
import com.webank.wedatasphere.exchangis.datax.core.processor.Processor;

import java.util.List;
import java.util.Map;

/**
 * ES8自定义处理器样例类 / ES8 Custom Processor Sample Class
 *
 * 核心职责 / Core responsibilities:
 * - 实现Processor<BulkOperationVariant>接口 / Implement Processor<BulkOperationVariant> interface
 * - 将列数据转换为BulkOperationVariant / Convert column data to BulkOperationVariant
 * - 支持IndexOperation（可扩展支持UpdateOperation等）/ Support IndexOperation (extensible to UpdateOperation, etc.)
 *
 * 使用场景 / Usage scenarios:
 * - 用户需要完全控制ES文档结构 / User needs full control over ES document structure
 * - 需要动态构建复杂的索引操作 / Need to dynamically build complex index operations
 * - 需要自定义文档ID / Need to customize document ID
 *
 * 参考ES6的CustomProcessor设计 / Reference ES6 CustomProcessor design
 *
 * @author davidhua
 * @since 2024-03-13
 */
public class CustomProcessor implements Processor<BulkOperationVariant> {

    /**
     * 索引名称 / Index name
     */
    private String indexName;

    /**
     * 文档ID / Document ID
     */
    private String documentId;

    /**
     * 构造函数 / Constructor
     */
    public CustomProcessor() {
    }

    /**
     * 处理列数据并转换为BulkOperationVariant / Process column data and convert to BulkOperationVariant
     *
     * @param columnData 列数据列表 / Column data list
     * @return BulkOperationVariant实例 (IndexOperation) / BulkOperationVariant instance (IndexOperation)
     * @throws Exception 处理异常 / Processing exception
     */
    @Override
    public BulkOperationVariant process(List<Object> columnData) throws Exception {
        // 将列数据转换为ES8文档 / Convert column data to ES8 document
        Map<String, Object> document = buildDocument(columnData);

        // 构建IndexOperation / Build IndexOperation
        IndexOperation.Builder builder = new IndexOperation.Builder();

        // 设置索引名称 / Set index name
        if (indexName != null) {
            builder.index(indexName);
        }

        // 设置文档ID / Set document ID
        if (documentId != null) {
            builder.id(documentId);
        }

        // 设置文档内容 / Set document content
        builder.document(document);

        return builder.build();
    }

    /**
     * 从列数据构建文档 / Build document from column data
     *
     * @param columnData 列数据列表 / Column data list
     * @return 文档Map / Document map
     */
    private Map<String, Object> buildDocument(List<Object> columnData) {
        // 实际实现中，用户应根据业务逻辑构建文档
        // In actual implementation, user should build document based on business logic

        // 示例：简单的数据转换 / Sample: Simple data transformation
        Map<String, Object> document = new java.util.HashMap<>();

        if (columnData != null && !columnData.isEmpty()) {
            // 示例：假设columnData[0]是field1, columnData[1]是field2, ...
            // Sample: Assume columnData[0] is field1, columnData[1] is field2, ...
            for (int i = 0; i < columnData.size(); i++) {
                document.put("field" + (i + 1), columnData.get(i));
            }
        }

        return document;
    }

    /**
     * 设置索引名称 / Set index name
     *
     * @param indexName 索引名称 / Index name
     * @return this
     */
    public CustomProcessor setIndexName(String indexName) {
        this.indexName = indexName;
        return this;
    }

    /**
     * 设置文档ID / Set document ID
     *
     * @param documentId 文档ID / Document ID
     * @return this
     */
    public CustomProcessor setDocumentId(String documentId) {
        this.documentId = documentId;
        return this;
    }

    /**
     * 使用示例 / Usage Example
     *
     * <pre>
     * // 创建自定义处理器 / Create custom processor
     * CustomProcessor processor = new CustomProcessor();
     * processor.setIndexName("my_index");
     * processor.setDocumentId("doc_123");
     *
     * // 处理列数据 / Process column data
     * List<Object> columnData = Arrays.asList("value1", "value2", "value3");
     * BulkOperationVariant operation = processor.process(columnData);
     *
     * // operation可以直接传递给startWrite(BasicDataReceiver, Class)
     * // operation can be directly passed to startWrite(BasicDataReceiver, Class)
     * </pre>
     *
     * 扩展示例：支持UpdateOperation / Extension example: Support UpdateOperation
     * <pre>
     * // 如果需要支持UpdateOperation，可以扩展此类：
     * // If need to support UpdateOperation, can extend this class:
     *
     * public class UpdateCustomProcessor extends CustomProcessor {
     *     {@literal @}Override
     *     public BulkOperationVariant process(List<Object> columnData) throws Exception {
     *         // 构建UpdateOperation / Build UpdateOperation
     *         Map<String, Object> doc = buildDocument(columnData);
     *         UpdateOperation.Builder builder = new UpdateOperation.Builder();
     *         builder.index(getIndexName());
     *         builder.id(getDocumentId());
     *         builder.doc(d -> d.document(doc));
     *         return builder.build();
     *     }
     * }
     * </pre>
     */
}

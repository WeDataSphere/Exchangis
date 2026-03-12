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

import com.webank.wedatasphere.exchangis.datax.plugin.writer.elasticsearchwriter.v8x.Elastic8xKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 基于模式的索引名称提取器 / Pattern-based index name extractor
 *
 * 核心职责 / Core responsibilities:
 * - 解析包含占位符的索引模式（如logs-{date}、order-{type}） / Parse index patterns with placeholders (e.g., logs-{date}, order-{type})
 * - 从文档数据中提取字段值填充占位符 / Extract field values from document data to fill placeholders
 * - 生成最终的动态索引名称 / Generate final dynamic index name
 *
 * 支持的占位符格式 / Supported placeholder format:
 * - {fieldName}: 使用文档中的字段值替换 / Replace with field value from document
 * - 示例: "logs-{date}" + {date: "2024-03-13"} => "logs-2024-03-13"
 * - 示例: "order-{type}" + {type: "online"} => "order-online"
 *
 * 字段值类型限制 / Field value type restrictions:
 * - 仅支持原始类型和String / Only supports primitive types and String
 * - 包括: String, Enum, 原始类型(int, long, double等), 包装类型(Integer, Long等)
 * - 不支持的类型返回空字符串 / Unsupported types return empty string
 *
 * 使用示例 / Usage example:
 * <pre>
 * // 创建提取器 / Create extractor
 * IndexExtractor extractor = new IndexPatternExtractor("logs-{date}");
 *
 * // 提取索引名 / Extract index name
 * Map<String, Object> data = new HashMap<>();
 * data.put("date", "2024-03-13");
 * String indexName = extractor.extractIndex(data); // 返回 / returns "logs-2024-03-13"
 * </pre>
 *
 * @author davidhua
 * @since 2024-03-13
 */
public class IndexPatternExtractor implements IndexExtractor{

    /**
     * 索引模式解析结果 / Index pattern parsing result
     * 包含静态字符串和动态字段提取函数的混合列表 / Mixed list of static strings and dynamic field extraction functions
     */
    private final List<Object> indexParts;

    /**
     * 构造函数，解析索引模式 / Constructor, parse index pattern
     *
     * @param index 索引模式字符串 / Index pattern string (e.g., "logs-{date}", "order-{type}-{status}")
     */
    public IndexPatternExtractor(String index){
        indexParts = parse(index);
    }

    /**
     * 从文档数据中提取并生成索引名称 / Extract and generate index name from document data
     *
     * @param columns 文档数据（列名->列值的映射）/ Document data (column name -> value mapping)
     * @return 填充占位符后的索引名称 / Index name with placeholders filled
     */
    @Override
    @SuppressWarnings("unchecked")
    public String extractIndex(Map<String, Object> columns){
        StringBuilder builder = new StringBuilder();
        for (Object part : indexParts){
            if (part instanceof Function<?, ?>){
                // 动态字段：从columns中提取值 / Dynamic field: extract value from columns
                builder.append(((Function<Map<String, Object>, String>) part).apply(columns));
            } else {
                // 静态字符串：直接拼接 / Static string: append directly
                builder.append(part);
            }
        }
        return builder.toString();
    }

    /**
     * 解析索引模式字符串为模板列表 / Parse index pattern string into template list
     *
     * 解析逻辑 / Parsing logic:
     * 1. 遍历字符串字符 / Traverse string characters
     * 2. 遇到'{'开始记录字段名 / Start recording field name on '{'
     * 3. 遇到'}'创建字段提取函数 / Create field extraction function on '}'
     * 4. 非占位符部分作为静态字符串 / Non-placeholder parts as static strings
     *
     * @param index 索引模式字符串 / Index pattern string
     * @return 模板列表（包含String和Function）/ Template list (contains String and Function)
     */
    private List<Object> parse(String index){
        List<Object> templates = new ArrayList<>();
        char[] chars = index.toCharArray();
        int start = 0;
        int sub = -1;

        // 遍历字符串解析占位符 / Traverse string to parse placeholders
        for(int i = 0; i < chars.length; i ++){
            char c = chars[i];
            if (c == Elastic8xKey.INDEX_PATTERN_START && sub < 0){
                // 遇到开始标记'{' / Encounter start marker '{'
                int len = i - start;
                if (len > 0){
                    // 添加静态字符串部分 / Add static string part
                    templates.add(newString(chars, start, len));
                }
                sub = i + 1;
            } else if (c == Elastic8xKey.INDEX_PATTERN_END && sub > 0){
                // 遇到结束标记'}' / Encounter end marker '}'
                int len = i - sub;
                if (len > 0){
                    String field = newString(chars, sub, len);
                    // 创建字段值提取函数 / Create field value extraction function
                    templates.add((Function<Map<String, Object>, String>)columns -> {
                        Object value = columns.get(field);
                        if (null != value) {
                            // 仅接受原始类型和String / Only accept primitive types and String
                            return value instanceof String || value instanceof Enum ||
                                    value.getClass().isPrimitive() ||
                                    isWrapClass(value.getClass()) ? String.valueOf(value) : "";
                        }
                        return "";
                    });
                }
                start = i + 1;
                sub = -1;
            }
        }

        // 添加剩余的静态字符串 / Add remaining static string
        if (start <= chars.length - 1){
            templates.add(newString(chars, start, chars.length - start));
        }
        return templates;
    }

    /**
     * 从字符数组中提取子字符串 / Extract substring from character array
     *
     * @param src 源字符数组 / Source character array
     * @param srcPos 起始位置 / Start position
     * @param len 长度 / Length
     * @return 子字符串 / Substring
     */
    private String newString(char[] src, int srcPos, int len){
        char[] dest = new char[len];
        System.arraycopy(src, srcPos, dest, 0, len);
        return new String(dest);
    }

    /**
     * 判断是否为包装类型 / Check if class is wrapper type
     *
     * 包装类型是指Java中包装原始类型的类 / Wrapper types are classes that wrap primitive types in Java
     * 如: Integer, Long, Double, Boolean等 / e.g., Integer, Long, Double, Boolean, etc.
     *
     * @param clz 要检查的类 / Class to check
     * @return true如果是包装类型 / true if wrapper type
     */
    private static boolean isWrapClass(Class clz){
        try{
            return ((Class)clz.getField("TYPE").get(null)).isPrimitive();
        }catch(Exception e){
            return false;
        }
    }
}

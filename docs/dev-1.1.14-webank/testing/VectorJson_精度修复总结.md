# 向量解析精度修复总结 / Vector Parsing Precision Fix Summary

## 问题描述 / Problem Description

在Elastic8xColumn的parseSparseVector和parseVector方法中，存在浮点数精度丢失问题。
原代码直接使用`asDouble()`然后转换为目标类型（float/double），这会损失精度。

In the parseSparseVector and parseVector methods of Elastic8xColumn, there was a floating-point precision loss issue.
The original code directly used `asDouble()` and then converted to the target type (float/double), which caused precision loss.

## 解决方案 / Solution

### 1. 创建VectorJson类 / Created VectorJson Class

**路径 / Path**: `exchangis-engines/engines/datax/datax-elasticsearch8xwriter/src/main/java/com/webank/wedatasphere/exchangis/datax/plugin/writer/elasticsearchwriter/v8x/column/VectorJson.java`

**核心特性 / Key Features**:
- 启用`DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS`配置
- 使用BigDecimal作为浮点数的中间表示
- 确保JSON解析时不会损失浮点数精度

### 2. 修改parseVector方法 / Modified parseVector Method

**修改前 / Before**:
```java
JsonNode jsonNode = Json.getMapper().readTree(rawData);
vector[i] = jsonNode.get(i).asDouble();
```

**修改后 / After**:
```java
JsonNode jsonNode = VectorJson.getMapper().readTree(rawData);
vector[i] = new BigDecimal(jsonNode.get(i).asText()).doubleValue();
```

### 3. 修改parseSparseVector方法 / Modified parseSparseVector Method

**修改前 / Before**:
```java
JsonNode jsonNode = Json.getMapper().readTree(rawData);
sparseVector.put(entry.getKey(), (float) entry.getValue().asDouble());
```

**修改后 / After**:
```java
JsonNode jsonNode = VectorJson.getMapper().readTree(rawData);
sparseVector.put(entry.getKey(),
    new BigDecimal(entry.getValue().asText()).floatValue());
```

## 技术细节 / Technical Details

### 精度保证机制 / Precision Guarantee Mechanism

1. **第一步 / Step 1**: 使用`asText()`将JsonNode转为字符串
   - 避免了JsonNode内部的double转换

2. **第二步 / Step 2**: 使用`new BigDecimal()`从字符串构造BigDecimal
   - BigDecimal能精确表示十进制浮点数

3. **第三步 / Step 3**: 使用`floatValue()`或`doubleValue()`转换为最终类型
   - 此时转换是基于精确的BigDecimal值，而不是之前可能已经损失精度的double值

### VectorJson配置 / VectorJson Configuration

```java
mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
```

## 测试验证 / Test Verification

### 新增测试用例 / New Test Cases

1. **should_PreserveFloatPrecision_When_ParsingSparseVector**
   - 验证SPARSE_VECTOR解析时的浮点数精度

2. **should_PreserveFloatPrecision_When_ParsingDenseVector**
   - 验证DENSE_VECTOR解析时的浮点数精度

3. **should_ParseSparseVector_When_ValidJsonObject**
   - 验证SPARSE_VECTOR基本功能

### 测试结果 / Test Results

```
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 影响范围 / Impact Scope

### 修改文件 / Modified Files

1. **Elastic8xColumn.java**
   - 导入VectorJson类
   - 修改parseVector方法（第364-382行）
   - 修改parseSparseVector方法（第396-413行）

2. **VectorJson.java** (新增)
   - 新建专门的向量解析JSON工具类

3. **Elastic8xColumnTest.java**
   - 新增3个精度相关测试用例

### 编译验证 / Build Verification

```bash
mvn clean compile -pl exchangis-engines/engines/datax/datax-elasticsearch8xwriter -am -DskipTests
# BUILD SUCCESS
```

## 使用说明 / Usage Instructions

### 对于DENSE_VECTOR类型 / For DENSE_VECTOR Type

输入格式：JSON数组字符串
```json
"[0.1, 0.2, 0.3, 0.4, 0.5]"
```

输出：`double[]` 数组，精度得到保证

### 对于SPARSE_VECTOR类型 / For SPARSE_VECTOR Type

输入格式：JSON对象字符串
```json
{"I": 0.55, "had": 0.4, "the": 0.3}
```

输出：`Map<String, Float>` 映射，精度得到保证

## 性能考虑 / Performance Considerations

- BigDecimal的引入会带来轻微的性能开销
- 但对于向量解析场景，数据量通常不大，性能影响可忽略
- 精度的保证远比轻微的性能损失更重要

## 总结 / Summary

通过引入VectorJson类和使用BigDecimal作为中间层，成功解决了向量解析时的浮点数精度丢失问题。
所有测试用例均通过，功能验证完整。

By introducing the VectorJson class and using BigDecimal as an intermediate layer, we successfully resolved the floating-point precision loss issue during vector parsing.
All test cases passed, and the functionality is fully verified.

---
**修改日期 / Date**: 2025-03-19
**修改人 / Author**: davidhua
**关联Issue / Related Issue**: 向量解析精度问题 / Vector parsing precision issue

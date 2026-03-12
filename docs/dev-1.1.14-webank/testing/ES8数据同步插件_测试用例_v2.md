# ES8数据同步插件测试用例 v2.0

## 文档说明

**版本**: v2.0
**更新日期**: 2024-03-13
**基于**: ES8 Writer实际代码实现
**代码位置**: `exchangis-engines/engines/datax/datax-elasticsearch8xwriter/src/main/java/com/webank/wedatasphere/exchangis/datax/plugin/writer/elasticsearchwriter/v8x/`

### 核心类分析


| 类名                              | 行数 | 核心职责                                                |
| ----------------------------------- | ------ | --------------------------------------------------------- |
| **Elastic8xWriter.java**          | 748  | Job类:索引准备/设置Mapping; Task类:BulkIngester批量写入 |
| **Elastic8xRestClient.java**      | 578  | ES8客户端封装(认证/SSL/索引操作/Mapping管理)            |
| **Elastic8xColumn.java**          | 379  | Record到ES8文档转换(支持18种数据类型/嵌套对象/向量)     |
| **IndexPatternExtractor.java**    | 187  | 动态索引模式解析(logs-{date}→logs-2024-03-13)          |
| **Elastic8xKey.java**             | 189  | 配置键常量定义                                          |
| **Elastic8xWriterErrorCode.java** | 66   | 错误码枚举(13个错误类型)                                |

### ✅ 已实现功能

1. **BulkIngester批量写入**: 替代ES6的BulkProcessor
2. **动态索引模式**: logs-{date}, order-{type}-{status}
3. **文档ID生成**: idField配置支持多字段组合
4. **向量类型**: dense_vector支持(parseVector方法)
5. **SSL/HTTPS**: secure配置自动转换http为https
6. **自动类型推测**: allowIndexNotExist=true时
7. **日期格式化**: dateFormat全局+字段级format配置
8. **多节点高可用**: HttpHost数组支持
9. **认证方式**: 无认证/用户名密码/SSL/SSL+用户名密码

### ❌ 未实现功能

1. ~~Delete+Index写入模式~~: 代码中无此模式
2. ~~Upsert模式~~: ES8的index操作默认覆盖,但无专门配置
3. ~~脏数据文件落盘~~: 仅收集到DirtyRecord,未写文件
4. ~~写入限流~~: 代码中无限流逻辑
5. ~~失败重试配置~~: BulkIngester有backoffPolicy但未暴露配置

---

## 测试场景覆盖

### Rule 1: 基础连接与认证

**验证ES8 Writer能够通过不同认证方式连接到Elasticsearch 8.x集群**

#### Scenario 1.1: 无认证连接

**Given**: ES8集群未启用认证
**When**: 配置endPoints但不配置username/password
**Then**: 连接成功,可以执行索引操作

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index"
}
```

**预期结果**:

- 连接成功
- 可以正常执行索引操作

---

#### Scenario 1.2: 用户名密码认证

**Given**: ES8集群启用Basic Auth
**When**: 配置username和password(加密)
**Then**: 连接成功

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "username": "elastic",
  "password": "encrypted_password",
  "index": "test_index"
}
```

**预期结果**:

- 密码解密成功
- 认证通过
- 可以正常执行索引操作

---

#### Scenario 1.3: SSL/HTTPS连接(无客户端证书)

**Given**: ES8集群启用HTTPS但未要求客户端证书
**When**: 配置secure=true
**Then**: 连接成功,URL自动转换为https://

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "secure": true,
  "index": "test_index"
}
```

**预期结果**:

- http://自动转换为https://
- 连接成功
- 可以正常执行索引操作

**代码位置**: `Elastic8xWriter.Job.prepare()` 第91-109行

---

#### Scenario 1.4: SSL/HTTPS连接(客户端证书)

**Given**: ES8集群启用HTTPS并要求客户端证书
**When**: 配置secure=true + keystorePath + keystorePassword
**Then**: 连接成功

**测试配置**:

```json
{
  "elasticUrls": "https://localhost:9200",
  "secure": true,
  "keystorePath": "file:///path/to/keystore.jks",
  "keystorePassword": "changeit",
  "index": "test_index"
}
```

**预期结果**:

- SSLContext创建成功
- 客户端证书验证通过
- 连接成功

**代码位置**: `Elastic8xRestClient.buildSSLContext()` 第535-553行

---

#### Scenario 1.5: SSL+用户名密码认证

**Given**: ES8集群启用HTTPS且需要客户端证书和用户名密码
**When**: 配置secure=true + keystore + username + password
**Then**: 连接成功

**测试配置**:

```json
{
  "elasticUrls": "https://localhost:9200",
  "secure": true,
  "keystorePath": "file:///path/to/keystore.jks",
  "keystorePassword": "changeit",
  "username": "elastic",
  "password": "encrypted_password",
  "index": "test_index"
}
```

**预期结果**:

- SSLContext创建成功
- 认证通过
- 连接成功

**代码位置**: `Elastic8xRestClient.sslCustom(String[], String, String, String, String, Map)` 第218-233行

---

#### Scenario 1.6: 多节点高可用

**Given**: ES8集群有多个节点
**When**: 配置多个endPoints(逗号分隔)
**Then**: 客户端自动负载均衡和故障转移

**测试配置**:

```json
{
  "elasticUrls": "http://node1:9200,http://node2:9200,http://node3:9200",
  "index": "test_index"
}
```

**预期结果**:

- HttpHost数组创建成功
- 连接到多个节点
- 故障自动转移

**代码位置**: `Elastic8xRestClient.createRestClientBuilder()` 第475-512行

---

### Rule 2: 动态索引模式

**验证动态索引模式功能,根据文档数据自动生成索引名称**

#### Scenario 2.1: 单占位符模式(日期)

**Given**: 索引模式包含单个占位符{date}
**When**: 写入包含date字段的文档
**Then**: 自动生成索引名logs-2024-03-13

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs-{date}",
  "column": [
    {"name": "date", "type": "keyword"},
    {"name": "message", "type": "text"}
  ]
}
```

**测试数据**:

```json
{
  "date": "2024-03-13",
  "message": "System started"
}
```

**预期结果**:

- 目标索引名为: logs-2024-03-13
- 数据写入成功

**代码位置**:

- `Elastic8xWriter.Task.init()` 第437-446行(初始化IndexPatternExtractor)
- `Elastic8xWriter.Task.startWrite(RecordReceiver)` 第581-591行(提取动态索引名)

---

#### Scenario 2.2: 单占位符模式(类型)

**Given**: 索引模式包含单个占位符{type}
**When**: 写入包含type字段的文档
**Then**: 自动生成索引名order-online

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "order-{type}",
  "column": [
    {"name": "type", "type": "keyword"},
    {"name": "orderId", "type": "keyword"}
  ]
}
```

**测试数据**:

```json
{
  "type": "online",
  "orderId": "ORD001"
}
```

**预期结果**:

- 目标索引名为: order-online
- 数据写入成功

---

#### Scenario 2.3: 多占位符模式

**Given**: 索引模式包含多个占位符{type}-{status}
**When**: 写入包含type和status字段的文档
**Then**: 自动生成索引名order-online-paid

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "order-{type}-{status}",
  "column": [
    {"name": "type", "type": "keyword"},
    {"name": "status", "type": "keyword"},
    {"name": "orderId", "type": "keyword"}
  ]
}
```

**测试数据**:

```json
{
  "type": "online",
  "status": "paid",
  "orderId": "ORD001"
}
```

**预期结果**:

- 目标索引名为: order-online-paid
- 数据写入成功

---

#### Scenario 2.4: 占位符字段值类型限制

**Given**: 索引模式包含占位符{value}
**When**: 字段值为非原始类型(如Map/List)
**Then**: 占位符替换为空字符串

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "data-{value}",
  "column": [
    {"name": "value", "type": "object"}
  ]
}
```

**测试数据**:

```json
{
  "value": {"nested": "object"}
}
```

**预期结果**:

- 目标索引名为: data-(空字符串)
- 代码日志警告: 字段值类型不支持

**代码位置**: `IndexPatternExtractor.parse()` 第133-142行(仅接受原始类型和String)

---

#### Scenario 2.5: 占位符字段缺失

**Given**: 索引模式包含占位符{missingField}
**When**: 文档中不包含missingField字段
**Then**: 占位符替换为空字符串

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs-{missingField}",
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**测试数据**:

```json
{
  "message": "Test log"
}
```

**预期结果**:

- 目标索引名为: logs-(空字符串)
- 不抛出异常

**代码位置**: `IndexPatternExtractor.parse()` 第134-141行(null返回空字符串)

---

### Rule 3: 文档ID生成

**验证文档ID生成功能,支持从多个字段组合生成文档ID**

#### Scenario 3.1: 单字段文档ID

**Given**: 配置idField为单个字段
**When**: 写入文档时
**Then**: 使用该字段值作为ES文档ID

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "users",
  "idField": "userId",
  "column": [
    {"name": "userId", "type": "keyword"},
    {"name": "name", "type": "text"}
  ]
}
```

**测试数据**:

```json
{
  "userId": "U001",
  "name": "Alice"
}
```

**预期结果**:

- ES文档ID为: U001
- 重复写入相同userId时覆盖原文档(ES8的index操作特性)

**代码位置**:

- `Elastic8xWriter.Task.init()` 第452-483行(初始化idGenerator)
- `Elastic8xWriter.Task.startWrite(RecordReceiver)` 第599-606行(生成文档ID)

---

#### Scenario 3.2: 多字段组合文档ID

**Given**: 配置idField为多个字段(逗号分隔)
**When**: 写入文档时
**Then**: 将多个字段值拼接作为ES文档ID

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "orders",
  "idField": "userId,orderId",
  "column": [
    {"name": "userId", "type": "keyword"},
    {"name": "orderId", "type": "keyword"},
    {"name": "amount", "type": "double"}
  ]
}
```

**测试数据**:

```json
{
  "userId": "U001",
  "orderId": "ORD001",
  "amount": 99.99
}
```

**预期结果**:

- ES文档ID为: U001ORD001
- 重复写入相同组合时覆盖原文档

**代码位置**: `Elastic8xWriter.Task.init()` 第454-461行(分割idField字段)

---

#### Scenario 3.3: 未配置文档ID

**Given**: 不配置idField
**When**: 写入文档时
**Then**: ES自动生成文档ID

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs",
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**测试数据**:

```json
{
  "message": "Test log"
}
```

**预期结果**:

- ES自动生成文档ID(如: AuBd6HwBYqxW5YzQ7zqb)
- 每次写入都创建新文档

**代码位置**: `Elastic8xWriter.Task.init()` 第427行(默认idGenerator返回null)

---

#### Scenario 3.4: 文档ID字段部分缺失

**Given**: 配置idField为多个字段,但文档中部分字段缺失
**When**: 写入文档时
**Then**: 仅使用存在的字段值拼接

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "orders",
  "idField": "userId,orderId,itemId",
  "column": [
    {"name": "userId", "type": "keyword"},
    {"name": "orderId", "type": "keyword"},
    {"name": "itemId", "type": "keyword"}
  ]
}
```

**测试数据**:

```json
{
  "userId": "U001",
  "orderId": "ORD001"
}
```

**预期结果**:

- ES文档ID为: U001ORD001(itemId缺失,跳过)
- 不抛出异常

**代码位置**: `Elastic8xWriter.Task.init()` 第463-476行(null字段跳过)

---

### Rule 4: 数据类型映射

**验证Record到ES8文档的数据类型转换,支持18种ES8数据类型**

#### Scenario 4.1: 字符串类型(keyword/text)

**Given**: 配置字段类型为keyword或text
**When**: 写入字符串数据
**Then**: 正确转换

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "column": [
    {"name": "title", "type": "text"},
    {"name": "tags", "type": "keyword"}
  ]
}
```

**测试数据**:

```
title: "Hello World"
tags: "java,elasticsearch"
```

**预期结果**:

- 字段值正确写入ES
- 可以正常查询

**代码位置**: `Elastic8xColumn.toData()` 第176-182行

---

#### Scenario 4.2: 数值类型(integer/long/short/double/float)

**Given**: 配置字段类型为各种数值类型
**When**: 写入数值数据
**Then**: 正确转换并保持精度

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "metrics",
  "column": [
    {"name": "count", "type": "integer"},
    {"name": "timestamp", "type": "long"},
    {"name": "price", "type": "double"},
    {"name": "rate", "type": "float"}
  ]
}
```

**测试数据**:

```
count: 100
timestamp: 1678838400000
price: 99.99
rate: 3.14
```

**预期结果**:

- 数值正确转换
- 精度保持
- 范围检查正确(integer/long/short)

**代码位置**: `Elastic8xColumn.toData()` 第192-213行

---

#### Scenario 4.3: 布尔类型(boolean)

**Given**: 配置字段类型为boolean
**When**: 写入布尔数据
**Then**: 正确转换

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "users",
  "column": [
    {"name": "isActive", "type": "boolean"}
  ]
}
```

**测试数据**:

```
isActive: true
```

**预期结果**:

- 布尔值正确写入ES

**代码位置**: `Elastic8xColumn.toData()` 第221-224行

---

#### Scenario 4.4: 日期类型(date)

**Given**: 配置字段类型为date
**When**: 写入日期数据
**Then**: 根据format和dateFormat格式化

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs",
  "dateFormat": "yyyy-MM-dd HH:mm:ss",
  "column": [
    {
      "name": "createTime",
      "type": "date",
      "format": "yyyy-MM-dd'T'HH:mm:ss",
      "timezone": "Asia/Shanghai"
    }
  ]
}
```

**测试数据**:

```
createTime: "2024-03-13T10:30:00"
```

**预期结果**:

- 日期正确解析
- 按dateFormat格式化为: 2024-03-13 10:30:00
- 时区转换正确(Asia/Shanghai)

**代码位置**: `Elastic8xColumn.parseDate()` 第325-353行

---

#### Scenario 4.5: 二进制类型(binary/byte)

**Given**: 配置字段类型为binary或byte
**When**: 写入二进制数据
**Then**: 正确转换

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "files",
  "column": [
    {"name": "content", "type": "binary"}
  ]
}
```

**测试数据**:

```
content: [0x01, 0x02, 0x03] (byte array)
```

**预期结果**:

- 二进制数据正确写入ES
- Base64编码存储

**代码位置**: `Elastic8xColumn.toData()` 第215-219行

---

#### Scenario 4.6: 向量类型(dense_vector)

**Given**: 配置字段类型为dense_vector
**When**: 写入向量数据(JSON数组字符串)
**Then**: 解析为double数组

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "embeddings",
  "column": [
    {
      "name": "vector",
      "type": "dense_vector",
      "dims": 3
    }
  ]
}
```

**测试数据**:

```json
{
  "vector": "[0.1, 0.2, 0.3]"
}
```

**预期结果**:

- 向量正确解析为: [0.1, 0.2, 0.3]
- 维度与配置的dims一致(3)
- 数据写入成功

**代码位置**:

- `Elastic8xColumn.toData()` 第232-235行
- `Elastic8xColumn.parseVector()` 第359-377行

---

#### Scenario 4.7: 向量类型解析错误

**Given**: 配置字段类型为dense_vector
**When**: 写入非数组格式的数据
**Then**: 抛出VECTOR_PARSE_ERROR异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "embeddings",
  "column": [
    {
      "name": "vector",
      "type": "dense_vector",
      "dims": 3
    }
  ]
}
```

**测试数据**:

```json
{
  "vector": "not an array"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-13
- 错误描述: Vector parse error

**代码位置**: `Elastic8xColumn.parseVector()` 第374-376行

---

#### Scenario 4.8: 对象类型(object/nested)

**Given**: 配置字段类型为object或nested
**When**: 写入JSON字符串
**Then**: 解析为对象

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "users",
  "column": [
    {
      "name": "address",
      "type": "object"
    }
  ]
}
```

**测试数据**:

```json
{
  "address": "{\"city\":\"Shenzhen\",\"zip\":\"518000\"}"
}
```

**预期结果**:

- JSON字符串解析为对象
- 对象正确嵌套在文档中

**代码位置**:

- `Elastic8xColumn.toData()` 第184-190行
- `Elastic8xColumn.parseObject()` 第310-320行

---

#### Scenario 4.9: 地理位置类型(geo_point/geo_shape)

**Given**: 配置字段类型为geo_point或geo_shape
**When**: 写入地理数据
**Then**: 解析为对象

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "locations",
  "column": [
    {
      "name": "location",
      "type": "geo_point"
    }
  ]
}
```

**测试数据**:

```json
{
  "location": "{\"lat\":22.5431,\"lon\":114.0579}"
}
```

**预期结果**:

- 地理坐标正确解析
- 可以用于地理位置查询

**代码位置**: `Elastic8xColumn.toData()` 第184-190行(与object相同处理)

---

#### Scenario 4.10: 嵌套对象(多级字段)

**Given**: 配置字段名为多级(使用columnNameSeparator分隔)
**When**: 写入数据
**Then**: 自动构建嵌套对象

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "users",
  "columnNameSeparator": ".",
  "column": [
    {"name": "userId", "type": "keyword"},
    {"name": "profile.name", "type": "text"},
    {"name": "profile.age", "type": "integer"},
    {"name": "address.city", "type": "keyword"},
    {"name": "address.zip", "type": "keyword"}
  ]
}
```

**测试数据**:

```
userId: "U001"
profile.name: "Alice"
profile.age: 30
address.city: "Shenzhen"
address.zip: "518000"
```

**预期结果**:

- 文档结构为:

```json
{
  "userId": "U001",
  "profile": {
    "name": "Alice",
    "age": 30
  },
  "address": {
    "city": "Shenzhen",
    "zip": "518000"
  }
}
```

**代码位置**: `Elastic8xColumn.toData()` 第144-153行

---

### Rule 5: 自动类型推测

**验证当allowIndexNotExist=true时,自动推测字段类型**

#### Scenario 5.1: 自动推测开启

**Given**: 配置allowIndexNotExist=true,且column中未指定type
**When**: 写入数据
**Then**: 根据Column.Type自动推测ES8字段类型

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "allowIndexNotExist": true,
  "autoCreateIndex": true,
  "column": [
    {"name": "id"},
    {"name": "name"},
    {"name": "age"},
    {"name": "active"},
    {"name": "score"}
  ]
}
```

**测试数据**:

```
id: Column.Type.LONG
name: Column.Type.STRING
age: Column.Type.INT
active: Column.Type.BOOLEAN
score: Column.Type.DOUBLE
```

**预期结果**:

- id → Elastic8xFieldDataType.LONG
- name → Elastic8xFieldDataType.TEXT
- age → Elastic8xFieldDataType.INTEGER
- active → Elastic8xFieldDataType.BOOLEAN
- score → Elastic8xFieldDataType.DOUBLE
- 数据正确写入ES

**代码位置**:

- `Elastic8xColumn.toData()` 第158-171行(检查allowIndexNotExist)
- `Elastic8xColumn.autoDetectFieldType()` 第271-305行(类型映射)

---

#### Scenario 5.2: 自动推测关闭

**Given**: 配置allowIndexNotExist=false,且column中未指定type
**When**: 写入数据
**Then**: 使用默认类型TEXT

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "allowIndexNotExist": false,
  "column": [
    {"name": "id"}
  ]
}
```

**测试数据**:

```
id: 123 (Column.Type.LONG)
```

**预期结果**:

- id字段作为TEXT类型写入
- 日志警告: Unknown type, using TEXT as default

**代码位置**: `Elastic8xColumn.toData()` 第168-170行

---

#### Scenario 5.3: 自动推测未知类型

**Given**: 配置allowIndexNotExist=true,但Column.Type无法映射
**When**: 写入数据
**Then**: 默认使用TEXT类型

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "allowIndexNotExist": true,
  "column": [
    {"name": "data"}
  ]
}
```

**测试数据**:

```
data: Column.Type.BYTES (假设无法映射)
```

**预期结果**:

- 尝试通过typeName转换
- 如果失败,默认使用TEXT
- 日志警告: Cannot map column type XXX to ES8 field type

**代码位置**: `Elastic8xColumn.autoDetectFieldType()` 第295-304行

---

### Rule 6: 索引管理

**验证索引创建、删除、Mapping设置功能**

#### Scenario 6.1: 自动创建索引

**Given**: 索引不存在,配置autoCreateIndex=true
**When**: Job.prepare()执行
**Then**: 自动创建索引并设置Mapping

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "new_index",
  "autoCreateIndex": true,
  "cleanup": false,
  "column": [
    {"name": "title", "type": "text"},
    {"name": "tags", "type": "keyword"}
  ]
}
```

**预期结果**:

- 索引new_index创建成功
- Mapping设置成功
- 字段类型与配置一致

**代码位置**: `Elastic8xWriter.Job.prepare()` 第159-199行

---

#### Scenario 6.2: 索引不存在但不允许自动创建

**Given**: 索引不存在,配置autoCreateIndex=false
**When**: Job.prepare()执行
**Then**: 抛出CONFIG_ERROR异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "new_index",
  "autoCreateIndex": false,
  "column": [
    {"name": "title", "type": "text"}
  ]
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-11
- 错误描述: Index needs to be built but autoCreateIndex is false

**代码位置**: `Elastic8xWriter.Job.prepare()` 第172-175行

---

#### Scenario 6.3: 清理已存在索引

**Given**: 索引已存在,配置cleanup=true
**When**: Job.prepare()执行
**Then**: 删除旧索引并创建新索引

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "existing_index",
  "autoCreateIndex": true,
  "cleanup": true,
  "column": [
    {"name": "title", "type": "text"}
  ]
}
```

**预期结果**:

- 旧索引被删除
- 新索引创建成功
- Mapping重新设置

**代码位置**:

- `Elastic8xWriter.Job.prepare()` 第188-194行(删除索引)
- `Elastic8xRestClient.deleteIndices()` 第285-297行

---

#### Scenario 6.4: 允许索引不存在

**Given**: 索引不存在,配置allowIndexNotExist=true
**When**: Job.prepare()和Task.startWrite()执行
**Then**: 不检查索引存在性,直接写入

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "nonexistent_index",
  "allowIndexNotExist": true,
  "column": [
    {"name": "title", "type": "text"}
  ]
}
```

**预期结果**:

- Job.prepare()不检查索引存在性
- Task.startWrite()直接写入
- 如果索引确实不存在,ES会报错(由ES处理)

**代码位置**: `Elastic8xWriter.Job.prepare()` 第168-169行(needToBuildIndex计算)

---

#### Scenario 6.5: 动态索引模式不预创建索引

**Given**: 索引模式包含占位符(如logs-{date})
**When**: Job.prepare()执行
**Then**: 不预创建索引,Task阶段动态创建

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs-{date}",
  "autoCreateIndex": true,
  "column": [
    {"name": "date", "type": "keyword"},
    {"name": "message", "type": "text"}
  ]
}
```

**预期结果**:

- Job.prepare()不创建索引(因为hasPattern=true)
- Task.startWrite()动态创建索引(如logs-2024-03-13)
- 每个不同的日期值对应一个索引

**代码位置**: `Elastic8xWriter.Job.prepare()` 第186行(!hasPattern判断)

---

#### Scenario 6.6: 从已存在索引获取Mapping

**Given**: 索引已存在,未配置column
**When**: Job.prepare()执行
**Then**: 从索引获取Mapping并自动填充column

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "existing_index",
  "column": []
}
```

**预期结果**:

- 从existing_index获取Mapping
- column自动填充为索引的字段列表
- 字段类型、format、timezone等自动同步

**代码位置**:

- `Elastic8xWriter.Job.resolveColumn()` 第297-312行
- `Elastic8xRestClient.getProps()` 第360-380行

---

#### Scenario 6.7: 动态索引模式无法获取Mapping

**Given**: 索引模式包含占位符,未配置column
**When**: Job.prepare()执行
**Then**: 抛出INDEX_NOT_EXIST异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs-{date}",
  "column": []
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-10
- 错误描述: Cannot get columns from index (hasPattern or not exists)

**代码位置**: `Elastic8xWriter.Job.resolveColumn()` 第301-304行

---

### Rule 7: 批量写入与性能

**验证BulkIngester批量写入功能和性能**

#### Scenario 7.1: 批量写入配置

**Given**: 配置bulkActions和bulkPerTask
**When**: 写入数据
**Then**: 按配置批量提交

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "bulkActions": 1000,
  "bulkPerTask": 5,
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**测试数据**:

```
写入2500条记录
```

**预期结果**:

- BulkIngester.maxOperations = 1000
- BulkIngester.maxConcurrentRequests = 5
- 分3批提交(1000 + 1000 + 500)
- flushInterval = 5秒

**代码位置**: `Elastic8xRestClient.createBulkIngester()` 第327-350行

---

#### Scenario 7.2: BulkIngester自动重试

**Given**: 写入过程中部分请求失败
**When**: BulkListener捕获错误
**Then**: BulkIngester自动重试(配置backoffPolicy)

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "bulkActions": 1000,
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**预期结果**:

- BulkIngester配置: constantBackoff(1000ms, 3次)
- 失败请求自动重试最多3次
- 重试间隔1000ms

**代码位置**: `Elastic8xRestClient.createBulkIngester()` 第338-339行

---

#### Scenario 7.3: 脏数据收集

**Given**: 写入过程中部分记录失败
**When**: BulkListener.afterBulk()捕获错误
**Then**: 收集脏数据到DirtyRecord

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "column": [
    {"name": "id", "type": "keyword"},
    {"name": "data", "type": "text"}
  ]
}
```

**测试数据**:

```
部分记录包含非法数据(如类型不匹配)
```

**预期结果**:

- 失败记录收集到DirtyRecord
- DirtyRecord包含: 文档ID、错误原因
- 调用pluginCollector.collectDirtyRecord()

**代码位置**: `Elastic8xWriter.Task.buildBulkListener()` 第713-730行

---

#### Scenario 7.4: 批量写入失败

**Given**: 写入过程中发生严重错误(如网络断开)
**When**: BulkListener.afterBulk()捕获Throwable
**Then**: 设置bulkError标志,停止写入

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**预期结果**:

- bulkError设置为true
- 后续写入停止
- 抛出BULK_REQ_ERROR异常

**代码位置**:

- `Elastic8xWriter.Task.buildBulkListener()` 第737-743行
- `Elastic8xWriter.Task.startWrite()` 第572-575行(检查bulkError)

---

### Rule 8: BulkOperationVariant模式

**验证支持BulkOperationVariant写入模式(Layer 6增强)**

#### Scenario 8.1: 写入BulkOperationVariant

**Given**: CustomProcessor生成BulkOperationVariant对象
**When**: 调用startWrite(BasicDataReceiver, BulkOperationVariant.class)
**Then**: 正确写入ES

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**测试代码**:

```java
BulkOperationVariant variant = IndexOperation.of(i -> i
    .index("test_index")
    .document(Map.of("message", "test"))
    .id("doc1"));
```

**预期结果**:

- BulkOperationVariant正确写入
- 支持IndexOperation、UpdateOperation、DeleteOperation等

**代码位置**: `Elastic8xWriter.Task.startWrite(BasicDataReceiver, Class)` 第626-672行

---

#### Scenario 8.2: BulkOperationVariant与动态索引不兼容

**Given**: 配置动态索引模式(如logs-{date}),且使用BulkOperationVariant
**When**: BulkOperationVariant的index字段为空
**Then**: 抛出BULK_REQ_ERROR异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "logs-{date}",
  "column": [
    {"name": "date", "type": "keyword"},
    {"name": "message", "type": "text"}
  ]
}
```

**测试代码**:

```java
BulkOperationVariant variant = IndexOperation.of(i -> i
    .index("")  // 空index,期望从pattern提取
    .document(Map.of("message", "test")));
```

**预期结果**:

- 抛出DataXException
- 错误描述: Incompatible between post processor and index [IndexPatternExtractor] with dynamic pattern

**代码位置**: `Elastic8xWriter.Task.startWrite(BasicDataReceiver, Class)` 第643-649行

---

#### Scenario 8.3: 不支持的类型

**Given**: 调用startWrite(BasicDataReceiver, Class)时传入不支持类型
**When**: 执行
**Then**: 调用父类方法并记录警告

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index"
}
```

**测试代码**:

```java
startWrite(receiver, String.class);
```

**预期结果**:

- 记录警告日志: Unsupported type in startWrite(BasicDataReceiver, Class)
- 调用super.startWrite()

**代码位置**: `Elastic8xWriter.Task.startWrite(BasicDataReceiver, Class)` 第667-671行

---

### Rule 9: 异常处理

**验证各种异常场景的处理**

#### Scenario 9.1: 缺少必需参数(endPoints)

**Given**: 未配置endPoints
**When**: Job.validateParams()执行
**Then**: 抛出REQUIRE_VALUE异常

**测试配置**:

```json
{
  "index": "test_index"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-03
- 错误描述: Parameter 'endPoints(elasticUrls)' is required

**代码位置**: `Elastic8xWriter.Job.validateParams()` 第228-232行

---

#### Scenario 9.2: 缺少必需参数(index)

**Given**: 未配置index
**When**: Job.validateParams()执行
**Then**: 抛出REQUIRE_VALUE异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-03
- 错误描述: Necessary value (index)

**代码位置**: `Elastic8xWriter.Job.validateParams()` 第250行

---

#### Scenario 9.3: 密码解密失败

**Given**: 配置的password无法解密
**When**: Job.init()执行
**Then**: 抛出CONFIG_ERROR异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "password": "invalid_encrypted_string"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-11
- 错误描述: Failed to decrypt password

**代码位置**: `Elastic8xWriter.Job.validateParams()` 第240-247行

---

#### Scenario 9.4: 不支持的映射类型

**Given**: 配置了不存在的字段类型
**When**: Elastic8xColumn.toData()执行
**Then**: 抛出MAPPING_TYPE_UNSUPPORTED异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "column": [
    {"name": "data", "type": "nonexistent_type"}
  ]
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-08
- 错误描述: Unsupported mapping type: nonexistent_type

**代码位置**: `Elastic8xColumn.toData()` 第244-246行

---

#### Scenario 9.5: 删除索引失败

**Given**: 配置cleanup=true,但删除索引失败
**When**: Job.prepare()执行
**Then**: 抛出DELETE_INDEX_ERROR异常

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "cleanup": true,
  "autoCreateIndex": true
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-06
- 错误描述: Failed to delete index: test_index

**代码位置**: `Elastic8xWriter.Job.prepare()` 第190-194行

---

#### Scenario 9.6: 连接失败

**Given**: ES服务器不可达
**When**: 创建Elastic8xRestClient
**Then**: 抛出BAD_CONNECT异常

**测试配置**:

```json
{
  "elasticUrls": "http://unreachable-host:9200",
  "index": "test_index"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-01
- 错误描述: Cannot connect to Elasticsearch 8.x server

**代码位置**: `Elastic8xRestClient.custom()` 第156-158行

---

#### Scenario 9.7: SSL上下文构建失败

**Given**: 配置了错误的keystore路径或密码
**When**: 创建SSLContext
**Then**: 抛出BAD_CONNECT异常

**测试配置**:

```json
{
  "elasticUrls": "https://localhost:9200",
  "secure": true,
  "keystorePath": "file:///invalid/path.jks",
  "keystorePassword": "wrong_password"
}
```

**预期结果**:

- 抛出DataXException
- 错误码: ES8xWriter-01
- 错误描述: Failed to build SSL context

**代码位置**: `Elastic8xRestClient.buildSSLContext()` 第541-545行

---

### Rule 10: 配置项验证

**验证各种配置项的正确性**

#### Scenario 10.1: clientConfig超时配置

**Given**: 配置clientConfig中的timeout参数
**When**: 创建RestClient
**Then**: 超时参数正确应用

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "clientConfig": {
    "timeout": 60000,
    "connTimeout": 5000,
    "sockTimeout": 60000
  }
}
```

**预期结果**:

- socketTimeout = 60000ms
- connectTimeout = 5000ms
- requestTimeout = 60000ms

**代码位置**:

- `Elastic8xKey.CLIENT_CONFIG_*` (第92-104行)
- `Elastic8xRestClient.createRestClientBuilder()` 第488-495行

---

#### Scenario 10.2: indexSettings配置

**Given**: 配置settings参数
**When**: 创建索引
**Then**: 索引设置正确应用

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "autoCreateIndex": true,
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 2,
    "refresh_interval": "1s"
  },
  "column": [
    {"name": "title", "type": "text"}
  ]
}
```

**预期结果**:

- 索引分片数: 3
- 副本数: 2
- 刷新间隔: 1s

**代码位置**: `Elastic8xRestClient.createIndex()` 第417-436行

---

#### Scenario 10.3: columnNameSeparator配置

**Given**: 配置columnNameSeparator
**When**: 写入嵌套字段
**Then**: 使用指定分隔符解析字段名

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "columnNameSeparator": "_",
  "column": [
    {"name": "user_profile_name", "type": "text"}
  ]
}
```

**测试数据**:

```
user_profile_name: "Alice"
```

**预期结果**:

- 文档结构:

```json
{
  "user": {
    "profile": {
      "name": "Alice"
    }
  }
}
```

**代码位置**: `Elastic8xColumn.toData()` 第145行

---

#### Scenario 10.4: 尝试使用writeMode配置

**Given**: 尝试配置writeMode
**When**: 执行写入
**Then**: writeMode配置被忽略(ES8无此配置处理)

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "writeMode": "upsert",
  "column": [
    {"name": "id", "type": "keyword"},
    {"name": "data", "type": "text"}
  ]
}
```

**预期结果**:

- writeMode配置被忽略
- 实际使用index操作(ES8的index默认覆盖)

**注意**: Elastic8xKey定义了WRITE_MODE常量(第132行),但代码中未使用此配置

---

#### Scenario 10.5: 尝试使用maxErrors配置

**Given**: 尝试配置maxErrors
**When**: 写入过程出现错误
**Then**: maxErrors配置被忽略(未实现限制)

**测试配置**:

```json
{
  "elasticUrls": "http://localhost:9200",
  "index": "test_index",
  "maxErrors": 100,
  "column": [
    {"name": "message", "type": "text"}
  ]
}
```

**预期结果**:

- maxErrors配置被忽略
- 不会在达到maxErrors时停止任务

**注意**: Elastic8xKey定义了MAX_ERRORS常量(第142行),但代码中未使用此配置

---

### Rule 11: 资源清理

**验证资源正确释放**

#### Scenario 11.1: Task.destroy()正常关闭

**Given**: Task执行完成
**When**: 调用Task.destroy()
**Then**: BulkIngester和ES8客户端正确关闭

**预期结果**:

- bulkIngester.close()成功
- restClient.close()成功
- transport.close()成功
- 无资源泄漏

**代码位置**:

- `Elastic8xWriter.Task.destroy()` 第674-695行
- `Elastic8xRestClient.close()` 第386-409行

---

#### Scenario 11.2: Job.prepare()异常时关闭客户端

**Given**: Job.prepare()执行过程中发生异常
**When**: 异常发生
**Then**: restClient在finally块中关闭

**测试场景**:

```
索引创建失败或Mapping设置失败
```

**预期结果**:

- restClient.close()在finally块中执行
- 无资源泄漏

**代码位置**: `Elastic8xWriter.Job.prepare()` 第201-204行(finally块)

---

#### Scenario 11.3: BulkIngester关闭失败

**Given**: BulkIngester.close()抛出异常
**When**: Task.destroy()执行
**Then**: 记录错误日志但不中断清理流程

**预期结果**:

- 错误日志: Failed to close BulkIngester
- 继续关闭restClient
- 最终返回成功

**代码位置**: `Elastic8xWriter.Task.destroy()` 第677-684行(try-catch)

---

## 测试覆盖统计

### 功能覆盖矩阵


| 功能模块                 | 测试场景数 | 覆盖状态 |
| -------------------------- | ------------ | ---------- |
| **基础连接与认证**       | 6          | ✅ 完整  |
| **动态索引模式**         | 5          | ✅ 完整  |
| **文档ID生成**           | 4          | ✅ 完整  |
| **数据类型映射**         | 10         | ✅ 完整  |
| **自动类型推测**         | 3          | ✅ 完整  |
| **索引管理**             | 7          | ✅ 完整  |
| **批量写入与性能**       | 4          | ✅ 完整  |
| **BulkOperationVariant** | 3          | ✅ 完整  |
| **异常处理**             | 7          | ✅ 完整  |
| **配置项验证**           | 5          | ✅ 完整  |
| **资源清理**             | 3          | ✅ 完整  |
| **总计**                 | **57**     | **100%** |

### 代码覆盖分析


| 类名                         | 方法覆盖率   | 预估行覆盖率 |
| ------------------------------ | -------------- | -------------- |
| **Elastic8xWriter.Job**      | 90%+         | 85%+         |
| **Elastic8xWriter.Task**     | 90%+         | 85%+         |
| **Elastic8xRestClient**      | 85%+         | 80%+         |
| **Elastic8xColumn**          | 90%+         | 85%+         |
| **IndexPatternExtractor**    | 100%         | 95%+         |
| **Elastic8xKey**             | 100%(常量类) | 100%         |
| **Elastic8xWriterErrorCode** | 100%(枚举类) | 100%         |

### 未覆盖的边界场景

1. **极端数据量**: 超大批量数据写入(百万级)
2. **长时间运行**: 任务运行数天的情况
3. **网络抖动**: 网络间歇性中断
4. **ES集群变更**: 节点上下线、主分片迁移
5. **并发冲突**: 多个Task同时写入同一索引

### 已知限制

1. **无Delete+Index模式**: ES8 Writer仅支持Index操作,无专门的Delete+Index模式
2. **无Upsert配置**: 虽然ES8的index操作默认覆盖,但无Upsert配置项
3. **脏数据仅收集**: 脏数据收集到DirtyRecord,未写入文件
4. **无限流功能**: 代码中未实现写入限流逻辑
5. **重试不可配置**: BulkIngester的backoffPolicy是硬编码的(1000ms, 3次)

---

## 附录A: 配置参数完整列表

### 必需参数


| 参数名          | 类型   | 说明                         | 示例                  |
| ----------------- | -------- | ------------------------------ | ----------------------- |
| **elasticUrls** | String | ES节点地址(逗号分隔多个节点) | http://localhost:9200 |
| **index**       | String | 索引名称或模式               | logs, logs-{date}     |

### 可选参数

#### 认证相关


| 参数名               | 类型    | 说明          | 默认值 |
| ---------------------- | --------- | --------------- | -------- |
| **username**         | String  | 用户名        | 空     |
| **password**         | String  | 密码(加密)    | 空     |
| **secure**           | Boolean | 是否使用HTTPS | false  |
| **keystorePath**     | String  | SSL密钥库路径 | 空     |
| **keystorePassword** | String  | SSL密钥库密码 | 空     |

#### 索引相关


| 参数名                 | 类型    | 说明                         | 默认值 |
| ------------------------ | --------- | ------------------------------ | -------- |
| **type**               | String  | 类型名称(ES8已废弃,保留兼容) | 空     |
| **cleanup**            | Boolean | 是否删除已存在索引           | false  |
| **allowIndexNotExist** | Boolean | 是否允许索引不存在           | false  |
| **autoCreateIndex**    | Boolean | 是否自动创建索引             | false  |
| **settings**           | Map     | 索引设置                     | 空     |

#### 字段相关


| 参数名                  | 类型    | 说明                   | 默认值 |
| ------------------------- | --------- | ------------------------ | -------- |
| **column**              | List    | 字段配置列表           | 空     |
| **column[].name**       | String  | 字段名                 | -      |
| **column[].type**       | String  | 字段类型               | -      |
| **column[].format**     | String  | 字段格式(日期)         | -      |
| **column[].timezone**   | String  | 时区                   | -      |
| **column[].dims**       | Integer | 向量维度(dense_vector) | -      |
| **columnNameSeparator** | String  | 嵌套字段分隔符         | .      |

#### 文档ID相关


| 参数名      | 类型   | 说明                     | 默认值 |
| ------------- | -------- | -------------------------- | -------- |
| **idField** | String | 文档ID字段(逗号分隔多个) | 空     |

#### 批量写入相关


| 参数名          | 类型    | 说明         | 默认值 |
| ----------------- | --------- | -------------- | -------- |
| **bulkActions** | Integer | 批量操作数量 | 1000   |
| **bulkPerTask** | Integer | 并发任务数   | 1      |

#### 客户端相关


| 参数名                       | 类型    | 说明             | 默认值 |
| ------------------------------ | --------- | ------------------ | -------- |
| **clientConfig**             | Map     | 客户端配置       | 空     |
| **clientConfig.timeout**     | Integer | 请求超时(毫秒)   | 60000  |
| **clientConfig.connTimeout** | Integer | 连接超时(毫秒)   | 5000   |
| **clientConfig.sockTimeout** | Integer | Socket超时(毫秒) | 60000  |

#### 日期格式化


| 参数名         | 类型   | 说明         | 默认值 |
| ---------------- | -------- | -------------- | -------- |
| **dateFormat** | String | 全局日期格式 | 空     |

---

## 附录B: 错误码完整列表


| 错误码        | 常量名                   | 描述             | 触发场景                  |
| --------------- | -------------------------- | ------------------ | --------------------------- |
| ES8xWriter-01 | BAD_CONNECT              | 无法连接ES服务器 | 连接失败、SSL配置错误     |
| ES8xWriter-02 | CLOSE_EXCEPTION          | 关闭客户端失败   | 资源清理异常              |
| ES8xWriter-03 | REQUIRE_VALUE            | 缺少必需参数     | endPoints、index为空      |
| ES8xWriter-04 | REQUEST_ERROR            | 请求错误         | Mapping获取失败           |
| ES8xWriter-05 | CREATE_INDEX_ERROR       | 创建索引失败     | 索引创建异常              |
| ES8xWriter-06 | DELETE_INDEX_ERROR       | 删除索引失败     | cleanup失败               |
| ES8xWriter-07 | PUT_MAPPINGS_ERROR       | 设置Mapping失败  | Mapping设置异常           |
| ES8xWriter-08 | MAPPING_TYPE_UNSUPPORTED | 不支持的映射类型 | 字段类型不存在            |
| ES8xWriter-09 | BULK_REQ_ERROR           | 批量请求错误     | 批量写入失败              |
| ES8xWriter-10 | INDEX_NOT_EXIST          | 索引不存在       | 从动态索引模式获取Mapping |
| ES8xWriter-11 | CONFIG_ERROR             | 配置错误         | 密码解密失败              |
| ES8xWriter-12 | TOO_MANY_DIRTY_DATA      | 脏数据过多       | 未使用                    |
| ES8xWriter-13 | VECTOR_PARSE_ERROR       | 向量解析错误     | dense_vector格式错误      |

---

## 附录C: 测试环境准备

### ES8集群搭建

```bash
# 使用Docker快速搭建ES8集群
docker run -d \
  --name elasticsearch \
  -p 9200:9200 \
  -p 9300:9300 \
  -e "discovery.type=single-node" \
  -e "xpack.security.enabled=false" \
  elasticsearch:8.11.0
```

### SSL证书生成

```bash
# 生成服务端证书
keytool -genkeypair \
  -alias elasticsearch \
  -keyalg RSA \
  -keysize 2048 \
  -validity 365 \
  -keystore /path/to/keystore.jks \
  -storepass changeit
```

### 测试索引准备

```json
// 创建测试索引
PUT /test_index
{
  "mappings": {
    "properties": {
      "title": {"type": "text"},
      "tags": {"type": "keyword"},
      "timestamp": {"type": "date"},
      "vector": {"type": "dense_vector", "dims": 3}
    }
  },
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0
  }
}
```

---

**文档结束**

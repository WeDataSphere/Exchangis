# ES8数据同步插件 - 设计文档（精简版）

## 文档信息

- **文档版本**: v1.0
- **最后更新**: 2026-03-12
- **维护人**: Claude Code
- **文档状态**: 草稿
- **需求类型**: NEW
- **需求文档**: [ES8数据同步插件_需求.md](../requirements/ES8数据同步插件_需求.md)

---

## 执行摘要

### 设计目标

| 目标 | 描述 | 优先级 |
|-----|------|:----:|
| 依赖完全隔离 | ES8插件与ES6插件完全隔离，可同时部署使用，无依赖冲突 | P0 |
| 高性能写入 | 单线程≥5000条/秒，多线程≥10000条/秒 | P0 |
| ES8新特性支持 | 支持ES8官方Java API Client 8.16.1、BulkIngester、vector向量类型 | P0 |
| 配置兼容性 | 配置参数与ES6插件保持相似 | P1 |
| 高可靠性 | 完善的错误处理、重试机制、脏数据管理 | P0 |

### 核心设计决策

| 决策点 | 选择方案 | 决策理由 |
|-------|---------|---------|
| 依赖隔离策略 | DataX插件ClassLoader隔离（框架原生支持） | DataX框架已提供ClassLoader隔离，每个插件使用独立的类加载器 |
| ES8客户端 | elasticsearch-java 8.16.1 | 官方推荐版本，支持最新ES8特性 |
| 批量写入实现 | BulkIngester（ES8新API） | 自动管理批量大小和并发，性能优于手动BulkRequest |
| 模块隔离 | 独立Maven模块datax-elasticsearch8xwriter | 物理隔离，便于独立维护 |
| 包路径隔离 | com.webank...elasticsearchwriter.v8x | 避免类名冲突，明确版本区分 |
| 写入模式实现 | Index/Upsert均使用client.index()方法 | ES8的index方法在指定ID时自带upsert语义 |

### 关键风险与缓解

| 风险 | 等级 | 缓解措施 |
|-----|:----:|---------|
| ES8客户端API不稳定 | 中 | 使用官方稳定版本8.16.1，充分测试 |
| 依赖冲突难以解决 | 高 | 使用DataX ClassLoader隔离 |
| 性能不达标 | 中 | 使用ES8的BulkIngester批量写入 |
| vector类型支持复杂 | 中 | 参考ES6插件的nested实现 |

### 核心指标

| 指标 | 目标值 | 说明 |
|-----|-------|------|
| 单线程写入性能 | ≥5000条/秒 | 数据为10个字段的简单文档 |
| 多线程写入性能 | ≥10000条/秒 | 5个并发线程 |
| 批量写入延迟 | ≤2秒/批次 | 批量大小=1000条 |
| 内存占用 | ≤512MB | 单个JVM进程 |
| 依赖隔离度 | 100% | DataX框架提供ClassLoader隔离 |

---

# Part 1: 核心设计

## 1.1 系统架构设计

### 1.1.1 架构模式

**采用模式**：分层架构 + 插件化架构

**选择理由**：
- **分层架构**：DataX框架本身采用分层架构，Writer插件需要适配框架的Job/Task分层
- **插件化架构**：独立Maven模块，支持动态加载，与ES6插件完全隔离
- **封装层**：Elastic8xRestClient封装ES8客户端API，隔离底层API变化

### 1.1.2 模块划分

| 模块 | 职责 | 对外接口 | 依赖 |
|-----|------|---------|------|
| **Elastic8xWriter.Job** | 任务准备阶段：参数校验、索引管理、字段映射解析、任务分片 | init(), prepare(), split() | Elastic8xRestClient, Elastic8xColumn |
| **Elastic8xWriter.Task** | 任务执行阶段：ES8客户端创建、批量写入、错误处理、统计上报 | start(), prepare(), post(), destroy() | Elastic8xRestClient, BulkIngester, Elastic8xColumn |
| **Elastic8xRestClient** | 封装ES8客户端：连接管理、索引操作、Mapping管理 | createIndex(), deleteIndex(), existIndices(), getMapping() | elasticsearch-java 8.16.1 |
| **Elastic8xColumn** | 字段映射和类型转换：解析字段配置、类型推断、向量转换 | toData() | 无 |

### 1.1.3 技术选型

| 层级 | 技术 | 版本 | 选型理由 |
|-----|------|------|---------|
| **ES8客户端** | elasticsearch-java | 8.16.1 | 官方推荐版本，支持最新ES8特性 |
| **批量写入** | BulkIngester | 8.16.1 | ES8新API，自动管理批量大小和并发 |
| **依赖隔离** | DataX ClassLoader | 框架原生支持 | DataX框架已提供ClassLoader隔离 |
| **日志框架** | SLF4J + Logback | 1.7.36 + 1.2.11 | 与项目现有日志框架一致 |

### 1.1.4 依赖隔离设计（核心）

**问题**：ES6插件使用elasticsearch-rest-high-level-client 6.7.1，ES8插件使用elasticsearch-java 8.16.1，两者存在大量类冲突。

**解决方案**：DataX框架已提供ClassLoader隔离机制，每个插件使用独立的类加载器。

**DataX ClassLoader隔离机制**：

```
DataX框架为每个插件创建独立的类加载器：
┌─────────────────────────────────────┐
│       DataX Engine Core             │
└─────────────────────────────────────┘
              │
    ┌─────────┴─────────┐
    │                   │
┌───▼────┐         ┌───▼────┐
│ES6插件  │         │ES8插件  │
│Plugin  │         │Plugin  │
│ClassLoader    │ClassLoader│
└────────┘         └────────┘
    │                   │
独立加载ES6依赖      独立加载ES8依赖
```

**优势**：
- 无需额外的Maven Shade插件配置
- 不增加jar包体积
- DataX框架原生支持，稳定可靠

---

## 1.2 核心流程设计

### 1.2.1 任务初始化流程（Job.prepare）

**流程说明**：在任务准备阶段，完成索引管理、字段映射解析、任务分片等操作。

**关键步骤**：
1. 读取配置（endPoints、indexName、username/password等）
2. 创建ES8客户端（认证、多节点）
3. 检查索引是否存在
4. 清空索引（如果配置cleanUp=true）
5. 创建索引（如果不存在或需要重建）
6. 解析字段映射配置
7. 任务分片（split）

**技术难点与解决方案**：

| 难点 | 解决方案 | 决策理由 |
|-----|---------|---------|
| 索引名称pattern表达式支持 | 使用正则表达式检测pattern（包含`{`和`}`），当索引包含pattern时跳过exists检查 | 支持pattern表达式可以动态生成索引名 |
| 清空索引性能 | 使用delete+create方式（删除整个索引后重建） | delete+create性能远优于deleteByQuery |
| 字段类型推断 | 基于allowIndexNotExist参数控制：true时自动推断，false时默认text类型 | 避免类型不匹配 |

### 1.2.2 数据写入流程（Task.start）

**流程说明**：在任务执行阶段，从Reader读取数据，转换为ES8文档，批量写入到ES8集群。

**关键步骤**：
1. 初始化BulkIngester（配置批量大小、并发数）
2. 从RecordReceiver读取数据
3. 遍历Record中的Column，根据Elastic8xColumn配置转换类型
4. 构建ES8文档（Map<String, Object>）
5. 添加到批量队列（BulkIngester.add）
6. 每10秒上报统计信息
7. 关闭BulkIngester（刷新剩余数据）

**技术难点与解决方案**：

| 难点 | 解决方案 | 决策理由 |
|-----|---------|---------|
| BulkIngester异步并发控制 | 配置maxConcurrent=5，bulkSize=1000 | ES8官方推荐值 |
| 向量类型转换 | 检测源数据类型：STRING时使用Jackson解析，OBJECT时直接转换 | 支持两种格式 |
| 错误阈值中止 | 使用AtomicInteger计数，达到阈值时调用interrupt() | 异步环境下有效 |

### 1.2.3 错误处理与重试流程

**关键步骤**：
1. 添加请求到BulkIngester
2. 批量写入（异步）
3. 可重试异常（429、网络超时）→ 自动重试3次
4. 重试成功 → 更新成功计数
5. 重试失败 → 记录脏数据
6. 检查错误阈值 → 达到阈值时中止任务

**重试策略**：指数退避（第1次等待1秒，第2次等待2秒，第3次等待4秒）

---

## 1.3 关键接口定义

> ⚠️ **注意**：本节仅包含接口签名和职责说明，不包含具体实现。接口设计完全参考ES6插件结构。

### 1.3.1 Elastic8xWriter.Job 接口

**核心职责**：
- 参数校验与解析（endPoints、indexName、username/password等）
- 索引管理（exists、create、delete）
- 字段映射解析与类型推断
- 任务分片（split）

**关键方法**：
```java
public class Elastic8xWriter extends Writer {
    public static class Job extends Writer.Job {
        @Override
        public void init();  // 参数校验

        @Override
        public void prepare();  // 索引管理、字段映射解析

        @Override
        public List<Configuration> split(int mandatoryNumber);  // 任务分片

        @Override
        public void destroy();  // 资源释放
    }
}
```

**参考ES6插件**：`exchangis-engines/engines/datax/datax-elasticsearchwriter/src/main/java/.../v6/ElasticWriter.java`

### 1.3.2 Elastic8xWriter.Task 接口

**核心职责**：
- 创建ES8客户端和BulkIngester
- 从Reader读取数据并转换为ES8文档
- 批量写入到ES8（自动刷新）
- 错误处理与重试
- 统计信息上报

**关键方法**：
```java
public static class Task extends Writer.Task {
    @Override
    public void prepare();  // 初始化客户端和BulkIngester

    @Override
    public void start();  // 数据写入主流程

    @Override
    public void post();  // 后处理：刷新数据、上报统计

    @Override
    public void destroy();  // 资源释放
}
```

**参考ES6插件**：同上，Task类的实现逻辑

### 1.3.3 Elastic8xRestClient 接口

**核心职责**：
- 封装ES8客户端创建逻辑（认证、多节点）
- 提供索引操作API（exists、create、delete）
- 提供Mapping管理API

**关键方法**：
```java
public class Elastic8xRestClient implements AutoCloseable {
    // 工厂方法：创建客户端（支持认证）
    public static Elastic8xRestClient custom(String[] endPoints,
        String username, String password, Map<String, Object> clientConfig);

    // 索引操作
    public boolean existIndices(String indexName);
    public boolean createIndex(String indexName, String indexType,
        Map<String, Object> settings, Map<Object, Object> props);
    public boolean deleteIndices(String indexName);

    // 关闭客户端
    @Override
    public void close();
}
```

**参考ES6插件**：`exchangis-engines/engines/datax/datax-elasticsearchwriter/src/main/java/.../v6/ElasticRestClient.java`

### 1.3.4 Elastic8xColumn 接口

**核心职责**：
- 定义字段映射配置（name、type、format、dims等）
- 提供静态方法toData处理Record到ES8文档的转换
- 处理嵌套对象（nested）和向量（vector）类型

**关键方法**：
```java
public class Elastic8xColumn {
    private String name;
    private String type;
    private String format;
    private String timezone;
    private Integer dims; // 仅dense_vector类型使用

    // 核心静态方法：转换Record为ES8文档
    public static Map<String, Object> toData(Record record,
        List<Elastic8xColumn> colConfs, String columnNameSeparator);

    // 私有辅助方法
    private static Object parseObject(String rawData);
    private static String parseDate(Elastic8xColumn config, Column column);
    private static double[] parseVector(Column column); // ES8新特性
}
```

**参考ES6插件**：`exchangis-engines/engines/datax/datax-elasticsearchwriter/src/main/java/.../v6/column/ElasticColumn.java`

### 1.3.5 Elastic8xFieldDataType 枚举

**核心职责**：定义所有支持的ES8数据类型

**枚举值**：
```java
public enum Elastic8xFieldDataType {
    TEXT, OBJECT, LONG, INTEGER, SHORT, BYTE,
    DOUBLE, FLOAT, HALF_FLOAT, SCALED_FLOAT,
    BINARY, BOOLEAN, DATE, KEYWORD, NESTED,
    DENSE_VECTOR  // ES8新特性
}
```

**参考ES6插件**：`exchangis-engines/engines/datax/datax-elasticsearchwriter/src/main/java/.../v6/column/ElasticFieldDataType.java`

### 1.3.6 核心业务规则

| 规则编号 | 规则描述 | 触发条件 | 处理逻辑 |
|---------|---------|---------|---------|
| BR-001 | 索引名称支持pattern表达式 | indexName包含`{`和`}` | 跳过exists检查，直接使用动态索引名写入 |
| BR-002 | 清空索引使用delete+create方式 | cleanUp=true | 先调用deleteIndices()，再调用createIndex() |
| BR-003 | 字段类型推断基于allowIndexNotExist参数 | 字段映射表中类型为空 | allowIndexNotExist=true时自动推断，false时默认text |
| BR-004 | nested对象嵌套深度≤3层 | 字段名包含分隔符 | 嵌套深度超过3层时记录错误并跳过 |
| BR-005 | vector字段必须配置dims参数 | type=dense_vector | 未配置dims时记录错误并跳过 |
| BR-006 | 错误数达到max_errors时立即中止任务 | failureCount>=max_errors | 调用Thread.currentThread().interrupt() |
| BR-007 | Upsert模式使用client.index()方法 | 写入模式=upsert | 使用index方法，通过指定文档_id实现覆盖或创建 |

---

## 1.4 设计决策记录 (ADR)

### ADR-001: 依赖隔离方案选择

- **状态**：已采纳
- **决策**：使用DataX框架的ClassLoader隔离机制
- **结论**：DataX框架已提供完整的ClassLoader隔离机制，无需额外工具
- **影响**：
  - 无需配置Maven Shade插件
  - jar包体积不增加
  - 确保ES6和ES8插件使用不同的包路径（如v6和v8x）

### ADR-002: 批量写入实现方案选择

- **状态**：已采纳
- **决策**：使用ES8的BulkIngester API
- **结论**：BulkIngester是ES8官方推荐的新API，自动管理批量大小和并发，性能优异
- **影响**：
  - 仅支持ES 7.16+版本
  - 需要配置监听器处理成功和失败回调

### ADR-003: 写入模式实现方案选择

- **状态**：已采纳
- **决策**：Index和Upsert模式均使用client.index()方法，通过是否指定文档_id区分行为
- **结论**：ES8的index方法在指定ID时本身就具备upsert语义
- **影响**：
  - 代码简洁，无需区分Index和Upsert
  - Upsert模式会覆盖整个文档

### ADR-004: 索引清空实现方案选择

- **状态**：已采纳
- **决策**：使用delete+create方式（删除整个索引后重建）
- **结论**：性能远优于deleteByQuery，且需求未要求不中断服务
- **影响**：
  - 清空索引时会有短暂中断（通常几秒）
  - Mapping需要重新创建

---

# Part 2: 支撑设计

## 2.1 数据模型设计

### 2.1.1 Elastic8xColumn 字段映射表

| 字段名 | 类型 | 说明 | 约束 |
|-------|------|------|------|
| name | String | ES8字段名（支持嵌套） | 必填 |
| type | String | ES8字段类型 | 可选 |
| format | String | 日期格式（仅date类型） | 可选 |
| timezone | String | 时区（仅date类型） | 可选 |
| dims | Integer | 向量维度（仅dense_vector） | type=dense_vector时必填 |

### 2.1.2 DirtyRecord 脏数据表

| 字段名 | 类型 | 说明 | 约束 |
|-------|------|------|------|
| timestamp | Long | 错误时间戳 | 必填 |
| rawData | String | 原始数据 | 必填 |
| errorMessage | String | 错误信息 | 必填 |
| errorType | String | 错误类型 | 必填 |

---

## 2.2 API规范设计

### 2.2.1 配置参数列表

| 参数名 | 类型 | 默认值 | 必填 | 说明 |
|-------|------|:----:|:----:|------|
| endPoints | String | - | 是 | ES8集群地址，多个用逗号分隔 |
| username | String | - | 否 | 基础认证用户名 |
| password | String | - | 否 | 基础认证密码 |
| indexName | String | - | 是 | 目标索引名称 |
| column | Array | - | 否 | 字段映射配置 |
| cleanup | Boolean | false | 否 | 写入前是否清空索引 |
| autoCreateIndex | Boolean | true | 否 | 索引不存在时是否自动创建 |
| writeMode | String | index | 否 | 写入模式：index/upsert |
| max_errors | Integer | 100 | 否 | 最大错误记录数 |

---

## 2.3 测试策略

### 2.3.1 关键测试场景

| 场景 | 输入 | 预期输出 | 优先级 |
|-----|------|---------|:----:|
| 基本数据写入 | MySQL表（100万条） | 全部成功，TPS≥5000 | P0 |
| Upsert模式 | 重复ID的数据 | 第二次更新文档 | P0 |
| 向量类型写入 | dense_vector字段 | 向量数据正确 | P0 |
| 索引清空 | cleanUp=true | 索引被重建 | P0 |
| 错误阈值中止 | max_errors=10 | 第10条时中止 | P0 |

---

## 2.4 外部依赖接口设计

### 2.4.1 Elasticsearch 8.x REST API

**接口地址**：http://localhost:9200（可配置多节点）
**认证方式**：Basic Authentication（用户名/密码）
**请求格式**：JSON
**响应格式**：JSON

**数据映射**：
- id → _id
- Record Column → document field
- STRING → text/keyword
- LONG → long
- DOUBLE → double
- DATE → date
- OBJECT → object/nested
- STRING/OBJECT → dense_vector

---

## 2.5 安全设计摘要

| 安全关注点 | 措施 | 说明 |
|-----------|------|------|
| 认证 | Basic Authentication | 支持密码加密存储 |
| 数据传输 | HTTP/HTTPS | 支持HTTPS加密 |
| 密码保护 | 日志脱敏 | 密码显示为**** |

---

# 附录

## A. 相关文档

- [需求文档](../requirements/ES8数据同步插件_需求.md)
- [ES6插件参考实现](exchangis-engines/engines/datax/datax-elasticsearchwriter)
- [Elasticsearch Java API Client 8.16文档](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/8.16/index.html)

## B. 更新日志

| 版本 | 时间 | 作者 | 变更说明 |
|------|------|------|---------|
| v1.0 | 2026-03-12 | Claude Code | 初版创建，完成完整设计 |
| v1.1 | 2026-03-12 | Claude Code | 根据用户反馈调整：ClassLoader隔离、pattern支持、Elastic8xColumn重设计 |

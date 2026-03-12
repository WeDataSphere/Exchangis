Feature: ES8数据同步插件
  在DataX引擎中新增独立的ES8数据写入插件，支持从多种数据源同步数据到Elasticsearch 8.x

  作为数据工程师
  我希望能够使用ES8 Writer插件将业务数据同步到Elasticsearch 8.x集群
  以便利用ES8的新特性和更好的性能

  Background:
    Given ES8集群已部署并正常运行
    And DataX任务已正确配置ES8 Writer插件
    And 源数据源（MySQL/Hive/HDFS/CSV）已准备就绪

  Rule: 插件必须与ES6插件完全隔离

    @smoke @critical
    Scenario: ES8和ES6插件可以同时部署使用
      Given 已部署ES6插件和ES8插件
      When 同时运行ES6任务和ES8任务
      Then 两个任务都应该成功完成
      And 不应该出现依赖冲突错误
      And ES6任务应该连接到ES6集群
      And ES8任务应该连接到ES8集群

  Rule: 数据源支持 - 从多种数据源读取数据并写入ES8

    @smoke @critical
    Scenario: 从MySQL读取数据并写入ES8
      Given MySQL数据库存在测试表"user"，包含10万条数据
      And 表结构为：id(int), name(varchar), age(int), create_time(datetime)
      When 配置DataX作业：
        | 参数 | 值 |
        | Reader | mysqlreader |
        | Writer | elasticsearch8writer |
        | 索引名称 | user_index |
        | 字段映射 | id→_id, name→name, age→age, create_time→created_at |
        | 写入模式 | index |
      And 启动同步任务
      Then 任务应该成功完成
      And ES8索引"user_index"应该包含10万条文档
      And 文档字段应该正确映射：
        | 字段 | 类型 | 示例值 |
        | _id | keyword | 12345 |
        | name | text | "张三" |
        | age | long | 25 |
        | created_at | date | "2024-01-01 12:00:00" |
      And 平均TPS应该≥5000条/秒

    @validation
    Scenario Outline: 数据类型转换正确性验证
      Given 输入数据包含不同类型的字段
      When 配置字段类型映射为DataX类型→ES8类型
      Then 数据类型转换应该正确

      Examples:
        | DataX类型 | ES8目标类型 | 测试数据 | 预期结果 |
        | STRING | text | "hello world" | 转换为text类型 |
        | LONG | long | 1234567890 | 转换为long类型 |
        | DOUBLE | double | 3.14159 | 转换为double类型 |
        | DATE | date | "2024-01-01 12:00:00" | 转换为date类型 |
        | BOOLEAN | boolean | true | 转换为boolean类型 |
        | NULL | - | null | 根据配置处理 |

    @negative
    Scenario: 输入字段缺失时记录错误
      Given 输入数据记录缺少必需字段"name"
      When 尝试写入ES8
      Then 该记录应该被标记为脏数据
      And 脏数据文件应该记录错误："Missing required field: name"

  Rule: 写入模式 - 支持Index/Upsert/Delete+Index三种模式

    @smoke @critical
    Scenario: Index模式 - 追加新文档
      Given ES8索引为空
      When 使用Index模式写入1000条新文档
      Then 写入结果应该是：
        | created | updated | failed |
        | 1000 | 0 | 0 |
      And 索引应该包含1000条文档

    @smoke @critical
    Scenario: Upsert模式 - 智能更新文档
      Given ES8索引已存在1000条文档，ID为1-1000
      And 准备1000条更新数据，ID为1-1000，更新字段"status"为"updated"
      When 使用Upsert模式写入
      Then 写入结果应该是：
        | created | updated | failed |
        | 0 | 1000 | 0 |
      And 所有文档的"status"字段应该是"updated"

    @critical
    Scenario: Upsert模式 - 不存在时自动创建
      Given ES8索引已存在500条文档，ID为1-500
      And 准备1000条数据，ID为1-1000
      When 使用Upsert模式写入
      Then 写入结果应该是：
        | created | updated | failed |
        | 500 | 500 | 0 |
      And 索引应该包含1000条文档

    @integration
    Scenario: Delete+Index模式 - 全量同步
      Given ES8索引已存在10000条旧文档
      When 使用Delete+Index模式写入5000条新文档
      Then 旧文档应该被清空
      And 索引应该仅包含5000条新文档
      And 日志应该记录："Cleared 10000 documents before indexing"

    @negative
    Scenario: Upsert模式未配置主键时报错
      Given 配置写入模式为Upsert
      And 未配置主键字段
      When 启动同步任务
      Then 任务应该立即失败
      And 错误信息应该包含："Primary key is required for Upsert mode"

  Rule: 认证与连接 - 支持基础认证和多节点高可用

    @smoke @critical
    Scenario: 使用用户名密码认证连接ES8
      Given ES8集群已启用基础认证
      And 用户名为"elastic"，密码为"password"
      When 配置ES8 Writer使用用户名密码认证
      And 启动同步任务
      Then 连接应该成功
      And 应该能够正常写入数据
      And 日志中不应该包含明文密码

    @negative
    Scenario: 使用错误的用户名密码连接失败
      Given ES8集群已启用基础认证
      When 配置错误的用户名"wrong"和密码"wrong"
      And 尝试连接ES8
      Then 连接应该失败
      And 错误信息应该包含："authentication failed"
      And 任务应该在60秒内中止

    @integration
    Scenario: 多节点高可用连接
      Given ES8集群有3个节点：es1:9200, es2:9200, es3:9200
      When 配置endpoint为所有节点地址
      And 启动同步任务
      Then 客户端应该能够轮询使用所有节点
      And 如果es1节点故障，应该自动切换到es2或es3
      And 任务应该成功完成

    @negative
    Scenario: ES8集群地址为空时报错
      Given 配置endpoint为空
      When 启动同步任务
      Then 任务应该立即失败
      And 错误信息应该包含："ES8 endpoint cannot be empty"

  Rule: 字段映射 - 支持自动推断和自定义映射

    @smoke @critical
    Scenario: 自动类型推断
      Given 输入数据包含字段：name(STRING), age(LONG), salary(DOUBLE)
      When 未配置字段映射，使用自动推断
      Then ES8文档字段类型应该是：
        | 字段 | 推断类型 |
        | name | text (包含keyword子字段) |
        | age | long |
        | salary | double |

    @validation
    Scenario: 自定义字段映射
      Given 输入数据包含字段：user_name, user_age
      When 配置字段映射为：
        | 源字段 | 目标字段 |
        | user_name | name |
        | user_age | age |
      And 启动同步任务
      Then ES8文档应该包含字段"name"和"age"
      And 不应该包含字段"user_name"和"user_age"

    @validation
    Scenario: 嵌套对象映射
      Given 输入数据包含字段：city, province, country
      When 配置字段映射为：
        | 源字段 | 目标字段 |
        | city | address.city |
        | province | address.province |
        | country | address.country |
      Then ES8文档应该包含嵌套对象：
        | address | 字段 |
        | object | city, province, country |

    @negative
    Scenario: 嵌套对象深度超过3层时报错
      Given 配置字段映射为："user.address.detail.room.info → value"
      When 尝试写入ES8
      Then 应该记录错误："Nested object depth exceeds 3 layers"
      And 该字段应该被跳过

  Rule: 索引管理 - 自动创建索引和清空索引

    @smoke @critical
    Scenario: 自动创建索引
      Given ES8集群不存在索引"new_index"
      When 配置autoCreateIndex=true
      And 启动同步任务
      Then 索引"new_index"应该被自动创建
      And 索引的settings应该是：
        | number_of_shards | number_of_replicas |
        | 3 | 1 |
      And 任务应该成功写入数据

    @negative
    Scenario: 索引不存在且autoCreateIndex=false时报错
      Given ES8集群不存在索引"new_index"
      When 配置autoCreateIndex=false
      And 启动同步任务
      Then 任务应该立即失败
      And 错误信息应该包含："Index does not exist and autoCreateIndex is false"

    @negative
    Scenario: 索引名称不符合规范时报错
      Given 配置索引名称为"InvalidIndex"（包含大写字母）
      When 启动同步任务
      Then 任务应该立即失败
      And 错误信息应该包含："Invalid index name"

    @integration
    Scenario: 清空索引后写入新数据
      Given ES8索引"test_index"已存在10000条文档
      When 配置cleanIndex=true
      And 启动同步任务写入5000条新数据
      Then 索引应该先被清空
      And 索引应该仅包含5000条新文档
      And 日志应该记录："Cleared 10000 documents from test_index"

  Rule: 性能优化 - 批量写入和多线程并发

    @smoke @critical
    Scenario: 单线程批量写入性能达标
      Given 准备10000条测试数据
      When 配置batchSize=5000, concurrency=1
      And 启动同步任务
      Then 总耗时应该≤2秒
      And 平均TPS应该≥5000条/秒
      And 内存占用应该≤512MB

    @smoke @critical
    Scenario: 多线程并发写入性能达标
      Given 准备50000条测试数据
      When 配置batchSize=5000, concurrency=5
      And 启动同步任务
      Then 总耗时应该≤5秒
      And 平均TPS应该≥10000条/秒
      And CPU占用应该≤80%

    @validation
    Scenario: 批量大小超出限制时使用默认值
      Given 配置batchSize=100000（超出50000限制）
      When 启动同步任务
      Then 应该使用默认值5000
      And 日志应该记录："batchSize exceeds limit, using default value 5000"

    @integration
    Scenario: 写入限流功能
      Given 配置tpsLimit=1000
      And 准备10000条测试数据
      When 启动同步任务
      Then 实际TPS应该不超过1000
      And 总耗时应该≥10秒

    @integration
    Scenario: 失败重试机制
      Given ES8集群不稳定，20%的请求会失败
      When 配置retryTimes=3, retryInterval=1000
      And 启动同步任务
      Then 失败的请求应该自动重试
      And 重试成功率应该≥90%
      And 最终任务应该成功完成

  Rule: 错误处理 - 脏数据记录和错误阈值中止

    @smoke @critical
    Scenario: 脏数据正确统计但不落盘
      Given 准备10条脏数据（类型错误、字段缺失）
      When 启动同步任务
      Then 脏数据统计数量应该为10条
      And 脏数据应该在内存中记录包含字段：
        | timestamp | raw_data | error_message | error_type |
      And 正常数据应该成功写入ES8
      And 脏数据不应该写入任何文件（不落盘）

    @smoke @critical
    Scenario: 错误率超过阈值时任务中止
      Given 准备100条测试数据，其中10条为脏数据
      When 配置errorThreshold=0.05（5%）
      And 启动同步任务
      Then 处理到第6条脏数据时任务应该立即中止
      And 错误率应该=10%（超过阈值）
      And 任务结果应该是"FAILED"

    @validation
    Scenario: 错误率未超过阈值时任务继续
      Given 准备1000条测试数据，其中50条为脏数据
      When 配置errorThreshold=0.1（10%）
      And 启动同步任务
      Then 任务应该成功完成
      And 950条正常数据应该成功写入
      And 50条脏数据应该被记录到文件

    @validation
    Scenario: 脏数据文件自动切分
      Given 配置maxDirtyRecords=1000
      And 准备2500条脏数据
      When 启动同步任务
      Then 应该生成3个脏数据文件
      And 每个文件最多包含1000条记录
      And 第3个文件应该包含500条记录

    @negative
    Scenario: 脏数据存储路径不可写时报错
      Given 配置脏数据存储路径为"/invalid/path"（不可写）
      When 启动同步任务
      Then 任务应该立即失败
      And 错误信息应该包含："Dirty data path is not writable"

  Rule: 统计信息上报 - 定期上报任务进度

    @validation
    Scenario: 每处理1000条数据上报一次统计
      Given 准备10000条测试数据
      When 启动同步任务
      Then 应该上报10次统计信息
      And 每次上报应该包含：
        | total_records | success_count | failed_count | error_rate |
      And 任务结束时应该上报最终统计

  Rule: 端到端集成测试 - 完整的数据同步流程

    @smoke @critical @integration
    Scenario: MySQL到ES8的完整数据同步
      Given MySQL数据库存在订单表"orders"，包含100万条数据
      And 表结构为：order_id, customer_id, amount, status, create_time
      And ES8集群已部署3个节点
      When 配置完整的DataX作业：
        | 参数 | 值 |
        | Reader | mysqlreader |
        | Writer | elasticsearch8writer |
        | 索引名称 | orders_index |
        | 写入模式 | upsert |
        | 主键字段 | order_id |
        | 批量大小 | 5000 |
        | 并发线程数 | 5 |
        | 错误阈值 | 0.05 |
      And 启动同步任务
      Then 任务应该成功完成
      And ES8索引"orders_index"应该包含100万条文档
      And 所有文档的字段应该正确映射
      And 平均TPS应该≥10000条/秒
      And 错误率应该<1%
      And 不应该有脏数据
      And 统计信息应该正确上报
      And 日志应该包含关键操作记录

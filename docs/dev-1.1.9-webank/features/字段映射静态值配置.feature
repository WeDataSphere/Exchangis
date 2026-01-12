# language: zh-CN
@datax @tidb @tdsql @static-value
Feature: 字段映射静态值配置
  在TiDB/TDSQL数据同步场景中,支持为目标表字段配置固定值,
  解决源表缺少字段时需要补充数据来源标识等业务需求。

  静态值通过DataX Reader端的SQL SELECT常量表达式实现。

  Background:
    Given 系统已启动
    And 数据库连接正常

  Rule: 静态值配置正确持久化和加载

    @smoke @critical
    Scenario: 保存包含静态值的作业配置
      Given 用户配置了以下字段映射:
        | 来源类型 | 来源字段/静态值 | 目标字段 |
        | 源字段 | order_id | order_id |
        | 静态值 | CRM | source_system |
      When 保存作业配置
      Then 作业应保存成功
      And 数据库中该作业的字段映射应包含静态值配置
      And 静态值配置的mapping_type应为"STATIC"
      And 静态值配置的static_value应为"CRM"

    @critical
    Scenario: 重新加载时静态值配置正确回显
      Given 存在一个包含静态值配置的作业
      And 该作业的source_system字段配置了静态值"CRM"
      When 加载作业配置
      Then source_system字段应识别为静态值类型
      And 该字段的静态值应为"CRM"

    @critical
    Scenario: 修改静态值后正确保存
      Given 存在一个包含静态值配置的作业
      And 该作业的source_system字段配置了静态值"CRM"
      When 将静态值修改为"ERP"
      And 保存作业配置
      Then 作业应保存成功
      And 数据库中静态值应更新为"ERP"

    @critical
    Scenario: 删除静态值映射后正确保存
      Given 存在一个包含2个静态值配置的作业
      When 删除其中一个静态值映射
      And 保存作业配置
      Then 作业应保存成功
      And 数据库中应只剩1个静态值配置

  Rule: 兼容旧版配置

    @critical
    Scenario: 旧版不含mapping_type的配置正常加载
      Given 数据库中存在旧版字段映射配置
      And 该配置不包含mapping_type字段
      When 加载该作业配置
      Then 所有字段映射应作为"NORMAL"类型处理
      And 系统应正常运行无报错

    @critical
    Scenario: 旧版配置添加静态值后正确保存
      Given 存在一个旧版配置的作业
      And 该配置不包含mapping_type字段
      When 添加一个新的静态值映射
      And 保存作业
      Then 作业应保存成功
      And 原有映射应自动标记为mapping_type="NORMAL"
      And 新增映射应标记为mapping_type="STATIC"

  Rule: DataX Reader正确生成静态值SQL

    @smoke @critical @integration
    Scenario: DataX任务Reader端正确生成包含静态值的SQL
      Given 存在一个包含静态值配置的同步作业:
        | 来源类型 | 来源字段/静态值 | 目标字段 |
        | 源字段 | order_id | order_id |
        | 源字段 | amount | amount |
        | 静态值 | CRM | source_system |
      And 源表名为"orders"
      When 构建DataX任务配置
      Then Reader的column配置应为:
        | column |
        | order_id |
        | amount |
        | 'CRM' as source_system |
      And 生成的SQL应为:
        """
        SELECT order_id, amount, 'CRM' as source_system FROM orders
        """

    @critical @integration
    Scenario: 多个静态值字段正确生成SQL
      Given 存在一个包含多个静态值配置的同步作业:
        | 来源类型 | 来源字段/静态值 | 目标字段 |
        | 源字段 | order_id | order_id |
        | 静态值 | CRM | source_system |
        | 静态值 | 202501 | batch_id |
        | 静态值 | 1 | is_synced |
      And 源表名为"orders"
      When 构建DataX任务配置
      Then 生成的SQL应为:
        """
        SELECT order_id, 'CRM' as source_system, '202501' as batch_id, 1 as is_synced FROM orders
        """

    @smoke @critical @integration
    Scenario: DataX任务正确写入静态值到目标表
      Given 存在一个包含静态值配置的同步作业:
        | 来源类型 | 来源字段/静态值 | 目标字段 |
        | 源字段 | order_id | order_id |
        | 静态值 | CRM | source_system |
      And 源表有以下数据:
        | order_id |
        | 1001 |
        | 1002 |
      When 执行该同步任务
      Then 任务应执行成功
      And 目标表应有以下数据:
        | order_id | source_system |
        | 1001 | CRM |
        | 1002 | CRM |

    @integration
    Scenario: 静态值类型自动转换
      Given 存在一个静态值配置
      And 静态值为"123"
      And 目标字段"amount"类型为"INT"
      When 执行同步任务
      Then 任务应执行成功
      And 目标表amount字段应写入整数123

    @negative @integration
    Scenario: 静态值类型转换失败时任务报错
      Given 存在一个静态值配置
      And 静态值为"ABC"
      And 目标字段"amount"类型为"INT"
      When 执行同步任务
      Then 任务应执行失败
      And 错误信息应包含"类型转换失败"
      And 错误信息应包含字段名"amount"

  Rule: Writer端column与Reader对应

    @critical @integration
    Scenario: Writer端column按顺序与Reader输出对应
      Given 存在一个包含静态值配置的同步作业:
        | 来源类型 | 来源字段/静态值 | 目标字段 |
        | 源字段 | order_id | order_id |
        | 源字段 | amount | amount |
        | 静态值 | CRM | source_system |
      When 构建DataX任务配置
      Then Reader的column应为:
        | column |
        | order_id |
        | amount |
        | 'CRM' as source_system |
      And Writer的column应为:
        | column |
        | order_id |
        | amount |
        | source_system |
      And Reader和Writer的column数量应相等

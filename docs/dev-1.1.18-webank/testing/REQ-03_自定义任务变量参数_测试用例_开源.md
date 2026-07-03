# 自定义任务变量参数 测试用例文档（开源版 / 后端 P0+P1）

> **需求编号**: REQ-03（开源版）
> **测试范围**: 后端 P0（DSS 动态变量注入链路）+ P1（变量 key 校验/重复检测）
> **本次范围说明**: 仅后端。前端 E2/E3/E4 相关用例（TC-P1-01/02/03/05/07/08/09/10）不在本次范围。
> **创建日期**: 2026-07-03

---

## 1. 测试范围与策略

### 1.1 测试目标

验证 REQ-03 后端 3 个文件变更的正确性与兼容性：
1. `JobParamValidator.java`（新增工具类）—— 核心校验逻辑，可独立单元测试
2. `ExchangisRefExecutionOperation.submit()`（appconn 端 P0）—— 变量提取入 payload
3. `ExchangisJobDssAppConnRestfulApi.executeJob/updateJob`（server 端 P0+P1）—— 变量合并 + 保存校验

### 1.2 测试分层

| 层级 | 测试对象 | 方式 | 覆盖场景 |
|:----:|---------|------|---------|
| **单元测试** | JobParamValidator | standalone main() 断言 | TC-P0-03/04/05/06/09, TC-P1-04/06 + 边界 |
| **链路验证** | appconn submit → server executeJob | 代码审查 + 编译验证 | TC-P0-07/08（依赖 DSS 运行时，单元测试 mock 受限） |
| **回归测试** | 现有插值链路 | 现有 JobUtilsRenderDtTest 不回归 | RG-01~RG-05 |

> **说明**：appconn→server 完整链路（TC-P0-07/08）依赖 DSS 框架运行时上下文（`executionRequestRef` 由 DSS 注入），纯单元测试难以 mock。本次以「代码审查 + 编译验证 + JobParamValidator 单元测试覆盖合并逻辑」为主，完整链路测试需集成环境（留待部署后人工/集成测试）。

---

## 2. 后端测试用例清单

### 2.1 P0 用例（DSS 动态变量注入）

| 用例ID | 场景 | 优先级 | 测试方法 | 预期结果 |
|--------|------|:------:|---------|---------|
| TC-P0-01 | DSS 变量 run_date 覆盖静态 jobParams | P0 | validateAndConvertParams + putAll 模拟 | DSS 值覆盖静态同名 key |
| TC-P0-02 | DSS 多变量全部生效 | P0 | validateAndConvertParams 多 key | 所有合法 key 转换并入 map |
| TC-P0-03 | 系统字段 nodeName/workspaceName/execUser/runDate 被过滤 | P0 | validateAndConvertParams 含系统字段 | 系统字段不出现在结果 map |
| TC-P0-04 | DSS 空 map / null → 不合并，使用静态 | P0 | validateAndConvertParams(null/empty) | 返回空 map，不阻断 |
| TC-P0-05 | 非法 key（数字开头/含特殊字符/含${）WARN 跳过 | P0 | validateAndConvertParams 含非法 key | 非法 key 跳过，合法 key 保留 |
| TC-P0-06 | null value 跳过 | P0 | validateAndConvertParams 含 null value | null value 的 key 跳过 |
| TC-P0-07 | appconn→server 变量传递链路 | P0 | 代码审查 + 编译验证 | submit() 提取 getVariables() 入 payload（编译通过） |
| TC-P0-08 | appconn getVariables() 返回 null | P0 | 代码审查（if null 不加 payload） | payload 无 variables key，行为同前 |
| TC-P0-09 | Object→String 类型转换（Integer 10 → "10"） | P0 | validateAndConvertParams 含 Integer | 结果 map 值为 String "10" |

### 2.2 P1 用例（变量校验 / 后端）

| 用例ID | 场景 | 优先级 | 测试方法 | 预期结果 |
|--------|------|:------:|---------|---------|
| TC-P1-04 | 后端保存拒绝非法 key | P1 | validateJobParams 含非法 key | 抛 IllegalArgumentException |
| TC-P1-06 | 后端保存拒绝重复 key | P1 | validateJobParams 含重复 key | 抛 IllegalArgumentException |
| TC-P1-04b | 后端保存拒绝超长 key（>64） | P1 | validateJobParams 含 65 字符 key | 抛 IllegalArgumentException |
| TC-P1-04c | 后端保存拒绝数量超限（>50） | P1 | validateJobParams 51 个 key | 抛 IllegalArgumentException |
| TC-P1-04d | 后端保存拒绝非法 JSON | P1 | validateJobParams 非法 JSON | 抛 IllegalArgumentException |
| TC-P1-06b | 合法 jobParams 通过校验 | P1 | validateJobParams 合法 JSON | 不抛异常 |
| TC-P1-06c | 空/blank jobParams 通过校验 | P1 | validateJobParams null/"" | 不抛异常 |

### 2.3 边界用例

| 用例ID | 场景 | 预期结果 |
|--------|------|---------|
| TC-B-01 | key 正好 64 字符 | 合法（validateJobParams 通过） |
| TC-B-02 | key 65 字符 | 非法（validateJobParams 拒绝） |
| TC-B-03 | key 含下划线开头 `_run_date` | 合法 |
| TC-B-04 | value 为空字符串 "" | 合法（String.valueOf("") = ""） |
| TC-B-05 | value 为 Boolean true | 转换为 "true" |
| TC-B-06 | value 为 Long | 转换为 String |
| TC-B-07 | validateAndConvertParams 空参数 map | 返回空 map（不抛异常） |
| TC-B-08 | SYSTEM_PARAM_KEYS 不可变 | 不可变 Set |

### 2.4 回归用例

| 用例ID | 场景 | 预期结果 |
|--------|------|---------|
| RG-01 | 无变量任务执行 | 不受影响（params 无 variables key → 跳过合并） |
| RG-02 | DSS 不传 params | 行为不变（variablesObj 非 Map → 跳过） |
| RG-03 | 现有 `${yesterday}` 表达式 | JobUtils.replaceVariable 不修改，正常工作 |
| RG-04 | jobParams JSON 格式 | 仍是 `{"key":"value"}`，无 schema 变更 |
| RG-05 | 现有 JobUtilsRenderDtTest | 不回归 |

---

## 3. 测试执行方式

### 3.1 JobParamValidator 单元测试

**测试类**: `exchangis-job/exchangis-job-common/src/test/java/com/webank/wedatasphere/exchangis/job/utils/JobParamValidatorTest.java`

**运行方式**: standalone main() 方法（与现有 JobUtilsRenderDtTest 一致，不依赖 JUnit 框架）

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-8.jdk/Contents/Home
cd /Users/davidhua/IdeaProjects/Exchangis
mvn clean test-compile -pl exchangis-job/exchangis-job-common -am -DskipTests -q
# 然后运行 main 方法（见测试报告）
```

### 3.2 链路验证

- **appconn submit()**: 编译验证通过（exchangis-appconn 模块编译 exit code 0）+ 代码审查确认 `executionRequestRef.getVariables()` 提取逻辑
- **server executeJob()**: 编译验证通过（exchangis-job-server 模块编译 exit code 0）+ 代码审查确认 `params.get("variables")` instanceof Map 判断 + putAll 合并
- **server updateJob()**: 编译验证通过 + 代码审查确认 `JobParamValidator.validateJobParams` 调用 + IllegalArgumentException 捕获返回 Message.error

---

## 4. 验收标准映射

| 验收标准 | 对应用例 | 验证方式 |
|---------|---------|---------|
| AC1.1 DSS params execUser 提取，其余 key 识别为变量 | TC-P0-03 | 单元测试 |
| AC1.2 DSS params 合并到 jobParamsMap，DSS 覆盖静态 | TC-P0-01, TC-P0-02 | 单元测试 + 代码审查 |
| AC1.3 执行后 source where dt='${run_date}' 被替换 | TC-P0-07（链路） | 集成测试（部署后） |
| AC2.2 后端保存接口收到非法 key 返回 400+错误 | TC-P1-04, TC-P1-04b | 单元测试 |
| AC2.3 校验失败的变量不被持久化 | TC-P1-04（validateJobParams 抛异常→updateJob 返回 error→不调 updateJob） | 代码审查 |
| AC3.2 后端保存接口收到重复 key 返回 400 | TC-P1-06 | 单元测试 |

---

**文档结束。测试聚焦后端 P0/P1 场景，JobParamValidator 单元测试覆盖核心校验逻辑；appconn→server 完整链路依赖 DSS 运行时，以编译验证 + 代码审查保证，完整链路测试留待集成环境。**

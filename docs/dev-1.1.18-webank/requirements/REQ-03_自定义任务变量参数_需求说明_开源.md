# 自定义任务变量参数 需求文档（开源版）

> **需求编号**: REQ-03（开源版）
> **版本**: Exchangis 1.1.18
> **需求类型**: ENHANCE（功能增强）
> **基础模块**: Exchangis 作业管理（exchangis-job）
> **文档版本**: v1.0
> **创建日期**: 2026-07-03
> **适用环境**: 开源 Exchangis（Apache Exchangis）

> 📌 **本文件是 `REQ-03_自定义任务变量参数_需求说明.md` 的开源版本**，剔除内部定制内容（内部环境引用、DSS 内部部署细节等），仅保留可贡献给开源社区的通用功能部分。REQ-03 本身是通用功能（任务变量定义 + DSS 动态传参 + 插值），开源版与内部版功能差异极小。

---

## 📋 需求速览

| 维度 | 内容 |
|-----|------|
| **一句话描述** | 为任务配置面板补全自定义 key=value 变量能力，打通 DSS 工作流动态传参链路，让 `${run_date}` 在执行时自动插值进源/目的过滤条件与写入规则 |
| **基础模块** | Exchangis 作业管理（`exchangis-job`） |
| **增强目的** | 补全 DSS 动态传参注入缺口（P0）+ 增加变量校验与编辑 UX（P1）+ 可选的类型/作用域扩展（P2） |
| **功能范围** | P0: 1 个 · P1: 3 个 · P2: 4 个 |
| **兼容性要求** | 向后兼容（无变量的任务零影响；jobParams JSON 格式不变） |
| **涉及模块** | 后端 `exchangis-job-server` + 前端 cluster 前端 |

---

## 1. 需求概述

### 1.1 业务背景

Exchangis 下，一个作业（Job）由若干子任务（SubJob）组成。每个子任务的配置（content）包含数据源 source 的过滤条件（`where`）、sink 的写入规则、字段映射等。

**当前现状**：这些配置字符串是**静态硬编码**的——例如 `where dt = '2026-07-01'`，每次执行若要更换日期值，用户必须编辑任务配置本体。

**业务痛点**：
- 数据同步任务的过滤条件（如分区日期 `dt`）、限速（`speed`）、并发数等会**频繁变更**；
- 经常需要由 DSS 工作流上游节点在执行时**动态传入**；
- 或由用户在执行前**临时指定**，而不修改任务配置本体。

### 1.2 核心目标

让用户在不修改任务配置本体的情况下，通过自定义变量（如 `run_date=2026-07-01`）动态控制同步任务的过滤条件、写入规则等，并支持 DSS 工作流运行时动态传参覆盖静态配置。

### 1.3 基础模块分析

**基础模块**: Exchangis 作业管理

**现有能力（已端到端存在，约 80%）**：

| 能力 | 现状 | 说明 |
|------|------|------|
| 变量定义 UI | ✅ 已存在 | 前端 configDrawer 的「任务变量」区块 |
| 变量持久化 | ✅ 已存在 | `ExchangisJobInfo.jobParams` JSON 字段存库 |
| 变量插值引擎 | ✅ 已存在 | `JobUtils.replaceVariable()` + Linkis `VariableUtils` |
| 插值注入点 | ✅ 已存在 | `GenericExchangisTransformJobBuilder.renderJobs()` 对 source/sink content 插值 |
| jobParams 流转 | ✅ 已存在 | 前端 → `ExchangisJobVo` → `ExchangisJobEntity` → `ExchangisJobInfo.jobParamsMap` → `SubExchangisJob` → `renderJobs()` 消费 |
| **DSS 动态传参注入** | ❌ **缺口（P0）** | `ExchangisJobDssAppConnRestfulApi.executeJob` 的 `params` 仅日志打印，未合并到 `jobInfo.jobParamsMap` |
| **变量校验规则** | ❌ 缺失（P1） | key 无命名校验、无重复检测、无数量上限 |
| **变量编辑 UX 增强** | ⚠️ 简陋（P1） | 仅普通文本框，无类型化控件、无常用变量提示 |

**增强动机**：本需求是「补全 + 增强」，而非从零新建。P0 改动极小（在 `executeJob` 内补 `putAll` + Object→String 转换）。

---

## 2. 现有功能分析（基于真实代码确认）

### 2.1 现有后端代码结构（`exchangis-job`）

#### 2.1.1 任务实体（ExchangisJobInfo）

**文件**: `exchangis-job-common/src/main/java/com/webank/wedatasphere/exchangis/job/domain/ExchangisJobInfo.java`

**关键字段**:
- `jobParams` (String, JSON): 持久化的变量 JSON，如 `{"run_date":"2026-07-01"}`
- `jobParamsMap` (`Map<String, String>`, `@JsonIgnore`): 运行时的变量 Map，由 `getJobParamsMap()` 从 `jobParams` JSON 反序列化得到

> ⚠️ **类型契约**：`jobParamsMap` 是 `Map<String, String>`。DSS `params` 是 `Map<String, Object>`。P0 合并时**必须**做 Object→String 转换（`String.valueOf(entry.getValue())`），否则类型不匹配。

#### 2.1.2 插值引擎（JobUtils.replaceVariable，复用）

**文件**: `exchangis-job-common/src/main/java/com/webank/wedatasphere/exchangis/job/utils/JobUtils.java`

**现有方法签名**:
```
replaceVariable(String source, Map<String, Object> variables) -> String
replaceVariable(String source, Map<String, Object> variables, long time) -> String
```

**现有行为**（复用不修改）:
- 调用 Linkis `VariableUtils.replace()` 做 `${var}` 替换
- `MARKER_HEAD = "r"` 前缀 trick：避免 source 开头的 `${` 被 VariableUtils 特殊处理
- `renderDt()` 二次解析日期表达式（`${yesterday}`、`${bdp.system.bizdate}` 等）
- **找不到 key 时保留 `${var}` 原样，不抛异常**

#### 2.1.3 插值注入点（renderJobs，已存在复用）

**文件**: `exchangis-job-server/src/main/java/com/webank/wedatasphere/exchangis/job/server/builder/transform/GenericExchangisTransformJobBuilder.java`

**现有行为**（不修改）:
- 遍历每个 `SubExchangisJob`
- 取 `subJob.getJobParams()`
- 对 `REALM_JOB_CONTENT_SOURCE` 和 `REALM_JOB_CONTENT_SINK` 两个 realm 的所有 String 值调用 `JobUtils.replaceVariable(value, jobParams, time)`

#### 2.1.4 DSS 动态传参缺口（P0 核心改动点）

**文件**: `exchangis-job-server/src/main/java/com/webank/wedatasphere/exchangis/job/server/restful/external/ExchangisJobDssAppConnRestfulApi.java`

**当前代码**:
- `executeJob(@PathVariable Long id, HttpServletRequest request, @RequestBody Map<String, Object> params)`
- 把 `params` 序列化成 JSON 字符串 `paramString`，仅 `LOG.info("Success to parse params content: {}", paramString)`
- 提取 `execUser = params.get("execUser")`
- `jobInfo = new ExchangisJobInfo(jobVo)` 从持久化数据构造
- `executeService.executeJob(loginUser, jobInfo, loginUser)`

> ⚠️ **缺失**: `params` map（除了 `execUser` 等系统字段）**从未被 `putAll` 到 `jobInfo.jobParamsMap`**。这是「DSS 动态传参不生效」的根因，是 P0 要补的核心逻辑。

### 2.2 现有前端代码结构

#### 2.2.1 任务变量编辑 UI（configDrawer.vue）

**文件**: `cluster/src/pages/jobManagement/components/configDrawer.vue`

**现有 UI 元素**:
- 「任务变量」标题
- 「+添加变量」按钮
- `v-for="(item, index) in formState.jobParams"` 渲染变量行（key 文本框 + `=` + value 文本框 + 删除按钮）

**现有 JS 逻辑**:
- `formState.jobParams = props.formData.jobParams || []`
- `watch` 监听 `formData` 变化，把对象 `{key: value}` 转成数组 `[{key, value}]`
- `createTask()` 添加前检查最后一行是否填完
- `deleteTask(index)` 删除行
- `handleOk()` 提交前仅校验 key/value 非空，**无 key 正则、无重复检测、无数量上限**

---

## 3. 5W2H 分析

| 维度 | 内容 |
|------|------|
| **What（是什么）** | 补全 DSS 动态变量注入链路 + 增加变量 key 校验/重复检测/UX 增强 |
| **Why（为什么）** | 当前任务配置静态硬编码，无法动态驱动；DSS 工作流传参链路断裂 |
| **Who（谁使用）** | 用户（编辑变量）、DSS 工作流调度（动态传参）、任务执行器（消费插值） |
| **When（什么时候）** | 任务执行时（DSS execute 调用 / 前端执行按钮调用）插值生效 |
| **Where（在哪里扩展）** | 后端 `ExchangisJobDssAppConnRestfulApi.executeJob`（P0 注入点）+ 前端 `configDrawer.vue`（P1 UX）+ 后端保存接口（P1 校验） |
| **How（怎么做）** | 复用现有 `JobUtils.replaceVariable` + `renderJobs()`，仅在 DSS execute 处 `putAll` 注入；前端增强校验与控件 |
| **How much（多少）** | 性能影响 < 50ms（字符串扫描，单 job 通常 < 10 个变量） |

---

## 4. 增强需求

### 4.1 功能总览

| ID | 增强点 | 优先级 | 状态 | 一句话描述 |
|----|-------|:------:|:----:|----------|
| E1 | DSS 动态变量注入 | **P0** | ✅ 已确认 | 把 DSS execute 接口的 params Map 合并到 jobInfo.jobParamsMap，DSS 优先覆盖静态配置 |
| E2 | 变量 key 命名校验 | **P1** | ✅ 已确认 | key 必须 `^[a-zA-Z_][a-zA-Z0-9_]*$`，长度 1-64，前后端双重校验 |
| E3 | 变量重复检测与冲突提示 | **P1** | ✅ 已确认 | 同一 job 内 key 唯一；前端实时标红，后端保存时拒绝 |
| E4 | 前端变量编辑 UX 增强 | **P1** | ✅ 已确认 | 变量名下拉提示常用变量、value 类型化控件、数量上限提示 |
| E5 | 变量值类型扩展 Object | P2 | ⚠️ 待分期 | 从 String 扩展为 Object（数字/布尔） |
| E6 | SubJob 级变量覆盖 | P2 | ⚠️ 待分期 | 支持 job 级 + SubJob 级变量 |
| E7 | 变量批量导入 | P2 | ⚠️ 待分期 | 从文本批量导入 key=value |
| E8 | 变量插值预览 | P2 | ⚠️ 待分期 | 编辑后预览插值效果 |

---

### 4.2 E1: DSS 动态变量注入 `P0` `已确认`

#### 增强描述

**原有功能**：`ExchangisJobDssAppConnRestfulApi.executeJob` 接收 DSS 工作流上下文的 `params` Map，但仅序列化成 JSON 字符串打日志，未注入到任务的 `jobParamsMap`。

**本次增强**：在 `executeJob` 内、调用 `executeService.executeJob` 之前，将 `params`（排除 `execUser` 等系统字段）合并到 `jobInfo.getJobParamsMap()`，且 DSS 的值覆盖静态 jobParams。

#### 输入变化

| 输入项 | 变化类型 | 说明 | 约束 |
|-------|:--------:|------|------|
| DSS params Map | 修改（语义变化，签名不变） | 当前仅打日志；改为合并到 jobParamsMap | key 同 E2 校验；value Object→String 转换；排除 `execUser` 等系统字段 |

#### 输出变化

| 输出项 | 变化类型 | 说明 |
|-------|:--------:|------|
| 任务执行时的 content 插值结果 | 修改 | DSS 传入的变量值会覆盖静态 jobParams |
| executeJob 响应 | 不变 | 仍返回 `Message.ok().data("jobExecutionId", id)` |

#### 业务规则

| 规则ID | 规则描述 |
|--------|---------|
| R1.1 | DSS `params` 的所有 key（除 `execUser`、`nodeName`、`workspaceName` 等系统字段外）一律作为业务变量合并到 `jobParamsMap` |
| R1.2 | 合并语义：`jobParamsMap.putAll(dssParams)`，即 **DSS 值覆盖静态 jobParams**（优先级：DSS > 静态） |
| R1.3 | Object→String 转换：`String.valueOf(entry.getValue())`，避免 `Map<String,String>` 类型不匹配 |
| R1.4 | DSS params 为 null 或空 Map 时，不合并，使用静态 jobParams（正常场景，不报错） |
| R1.5 | DSS params key 不符合 E2 命名规则时，**记录 WARN 日志并跳过该 key**（不阻断执行） |
| R1.6 | 合并发生在 `executeService.executeJob(...)` 调用之前 |
| R1.7 | 合并后的 `jobParamsMap` 通过现有链路流转到 `renderJobs()` 消费插值 |

#### 验收标准（三段式）

| 验证阶段 | 验收条件 |
|:--------:|---------|
| 【输入验证】 | AC1.1: DSS execute 接口收到 params 后，`execUser` 被正确提取为执行用户；其余业务 key 被识别为变量 |
| 【处理验证】 | AC1.2: DSS params 被合并到 `jobInfo.jobParamsMap`；DSS 值覆盖静态 jobParams 同名 key |
| 【输出验证】 | AC1.3: 执行后任务 source 的 `where dt = '${run_date}'` 被替换为 DSS 传入的 `run_date` 值 |

---

### 4.3 E2: 变量 key 命名校验 `P1` `已确认`

#### 业务规则

| 规则ID | 规则描述 |
|--------|---------|
| R2.1 | 变量 key 正则：`^[a-zA-Z_][a-zA-Z0-9_]*$`（字母或下划线开头，仅含字母数字下划线） |
| R2.2 | 变量 key 长度：1-64 字符 |
| R2.3 | 禁止含 `${`、`}`、空格、中文等特殊字符 |
| R2.4 | 前端实时校验：输入时即时标红 + 提示 |
| R2.5 | 后端保存接口接收 jobParams 时复用同一校验方法，失败返回 400 + 错误信息 |
| R2.6 | 后端 DSS execute 的 params key 走同一校验（不合规跳过并 WARN） |

#### 验收标准（三段式）

| 验证阶段 | 验收条件 |
|:--------:|---------|
| 【输入验证】 | AC2.1: 前端输入非法字符时，输入框即时标红并提示错误 |
| 【处理验证】 | AC2.2: 后端保存接口收到非法 key 时返回 400 + 错误信息 |
| 【输出验证】 | AC2.3: 校验失败的变量不被持久化 |

---

### 4.4 E3: 变量重复检测与冲突提示 `P1` `已确认`

#### 业务规则

| 规则ID | 规则描述 |
|--------|---------|
| R3.1 | 同一 job 内变量 key 唯一 |
| R3.2 | 前端实时检测：编辑任一行的 key 时，若与已存在的 key 重复，重复的所有行标红提示 |
| R3.3 | 后端保存接口校验 key 唯一性，重复时返回 400 |
| R3.4 | 变量数量上限：50 个/job；前端达到上限时「+添加变量」按钮禁用 |

#### 验收标准（三段式）

| 验证阶段 | 验收条件 |
|:--------:|---------|
| 【输入验证】 | AC3.1: 前端添加重复 key 时，重复行即时标红 |
| 【处理验证】 | AC3.2: 后端保存接口收到重复 key 时返回 400 |
| 【输出验证】 | AC3.3: 变量数量达到 50 时按钮禁用并提示上限 |

---

### 4.5 E4: 前端变量编辑 UX 增强 `P1` `已确认`

#### 业务规则

| 规则ID | 规则描述 |
|--------|---------|
| R4.1 | key 输入框提供常用变量下拉提示：`run_date`、`run_month`、`biz_date`、`biz_month` |
| R4.2 | value 输入提供两种模式切换：「字面量」（普通文本）/「日期表达式」（日期选择器） |
| R4.3 | 变量数量达到 50 时按钮禁用（见 R3.4） |
| R4.4 | 提交前前端先做完整校验，通过后再调用后端接口 |

#### 验收标准（三段式）

| 验证阶段 | 验收条件 |
|:--------:|---------|
| 【输入验证】 | AC4.1: key 输入时下拉提示常用变量 |
| 【处理验证】 | AC4.2: value 可切换「字面量/日期表达式」模式 |
| 【输出验证】 | AC4.3: 重新打开配置抽屉时，变量列表正确回显 |

---

### 4.6 E5-E8: P2 待分期增强点（概要）

| ID | 增强点 | 核心难点 |
|----|-------|---------|
| E5 | 变量值类型扩展 Object | `jobParamsMap` 改为 `Map<String,Object>`，影响序列化与下游消费 |
| E6 | SubJob 级变量覆盖 | 扩展 `SubExchangisJob` 数据结构 |
| E7 | 变量批量导入 | 前端文本解析，复用校验 |
| E8 | 变量插值预览 | 新增预览接口或前端本地模拟 |

---

## 5. 兼容性分析

### 5.1 接口兼容性

| 接口 | 影响 | 兼容方案 |
|------|------|---------|
| `POST /dss/exchangis/main/appJob/execute/{id}` | ✅ 增强（P0） | **签名不变**；行为增强：params 现在会被合并。对历史 DSS 调用方**完全向后兼容** |
| `PUT /dss/exchangis/main/appJob/{id}` | ✅ 增强（P1） | **签名不变**；新增 key 校验。仅校验本次提交，不动历史数据 |
| `POST /dss/exchangis/main/job/{id}/execute`（内部 execute） | ✅ 无影响 | 不改动 |

### 5.2 数据兼容性

| 维度 | 影响 | 方案 |
|------|------|------|
| `jobParams` JSON 字段格式 | ✅ 无变化 | 仍是 `{"key":"value"}` 格式 |
| `jobParamsMap` 类型 | ✅ 无变化（P0/P1） | 仍是 `Map<String,String>` |
| 历史 jobParams 数据 | ⚠️ 可能含非法 key | **不做数据迁移**；回显时不阻断 |

### 5.3 行为兼容性

| 场景 | 影响 |
|------|------|
| 无变量的任务执行 | ✅ 无影响 |
| 不传 params 的 DSS 调用 | ✅ 无影响 |
| 现有 `${yesterday}` 日期表达式 | ✅ 无影响 |
| Linkis EngineConn 层 | ✅ 无影响（插值在 transform builder 完成） |

**灰度发布建议**：改动小且向后兼容，**无需配置开关**。

---

## 6. 非功能需求

### 6.1 性能需求

| 指标 | 要求 |
|------|------|
| 变量插值延迟 | < 50ms / job |
| DSS params 合并延迟 | < 5ms |
| 前端校验延迟 | < 100ms / 输入 |

### 6.2 安全需求

| 维度 | 要求 |
|------|------|
| key 注入防护 | 正则校验禁 `${`、`}` |
| value SQL 注入 | 不阻断（走下游参数化）；日志 WARN 记录可疑字符 |
| 敏感信息 | 不支持（密码走现有数据源密码加密机制） |

### 6.3 可观测性需求

| 维度 | 要求 |
|------|------|
| DEBUG 日志 | 记录合并前后的 jobParamsMap；记录插值前后的 content 片段 |
| WARN 日志 | 记录未解析变量；记录不合规的 DSS params key；记录含风险字符的 value |

---

## 7. 关联影响分析

### 7.1 功能模块影响

| 模块 | 影响等级 | 说明 |
|------|:--------:|------|
| DSS AppConn 执行链路 | 🟢 轻微 | 新增 params 注入逻辑，向后兼容 |
| 任务保存接口 | 🟢 轻微 | 新增校验，向后兼容 |
| 任务执行链路 | 🟢 轻微 | 插值逻辑复用 |
| 前端任务配置 UI | 🟢 轻微 | 增强 UX |

### 7.2 数据模型影响

| 维度 | 影响等级 |
|------|:--------:|
| 表结构 | 🟢 无影响 |
| 数据迁移 | 🟢 无影响 |
| 数据一致性 | 🟢 无影响 |

### 7.3 安全与权限影响

| 维度 | 影响等级 |
|------|:--------:|
| 权限点 | 🟢 无新增（复用现有 JOB_ALTER/JOB_EXECUTE） |
| 安全风险 | 🟢 低（key 校验防注入） |

### 7.4 提示词注入风险

**风险等级**: **N/A** — 本需求是纯 Java 业务需求，不涉及 Claude 插件开发，本维度不适用。

---

## 8. 验收标准汇总

### 8.1 增强点验收标准（共 12 条，三段式）

见各增强点章节（AC1.1-AC1.3, AC2.1-AC2.3, AC3.1-AC3.3, AC4.1-AC4.3）。

### 8.2 兼容性验收标准

- [ ] 现有无变量任务执行正常
- [ ] 现有 `${yesterday}` 日期表达式插值正常（不回归）
- [ ] 现有 DSS 调用不传 params 时行为不变
- [ ] 现有 jobParams JSON 格式不变
- [ ] 内部执行接口行为不变

---

## 9. 涉及文件清单

### 9.1 需要修改的文件（后端 `exchangis-job`）

- [ ] `exchangis-job-server/.../restful/external/ExchangisJobDssAppConnRestfulApi.java` - **P0**: `executeJob` 内补 params 合并；**P1**: `updateJob` 新增校验
- [ ] 新增 `exchangis-job-common/.../utils/JobParamValidator.java`（建议）- **P1**: 统一 key 校验工具

### 9.2 需要修改的文件（前端）

- [ ] `cluster/src/pages/jobManagement/components/configDrawer.vue` - **P1**: key 正则校验、重复检测、数量上限、下拉提示、value 模式切换

### 9.3 复用不修改的文件

- `exchangis-job-common/.../utils/JobUtils.java` - 插值引擎（复用 `replaceVariable`）
- `exchangis-job-server/.../builder/transform/GenericExchangisTransformJobBuilder.java` - 插值注入点（复用 `renderJobs`）
- `exchangis-job-common/.../domain/ExchangisJobInfo.java` - 实体（复用 `jobParamsMap`）

---

## 10. 风险识别

| 风险类别 | 风险 | 应对 |
|---------|------|------|
| 兼容性 | 历史 jobParams 含非法 key，更新时被拒绝 | 仅校验本次提交；历史数据回显不阻断 |
| 兼容性 | DSS 历史调用方传非标准 params key | WARN 跳过该 key，不阻断执行 |
| 技术 | Object→String 转换丢失类型信息 | P0 仅 String；P2 扩展 Object 时再处理 |
| 业务 | 用户误用变量传递敏感信息 | 文档明确「不支持敏感信息」 |

---

## 11. 边界条件与异常处理

| 场景 | 处理策略 |
|------|---------|
| 变量 key 含 `${` | 前端阻止 + 后端拒绝 |
| 变量 value 含未闭合 `${` | 保留原样，不报错 |
| 变量循环引用 | Linkis VariableUtils 现有逻辑保留未解析部分 |
| DSS params 为 null/空 map | 不合并，使用静态 jobParams |
| DSS params key 与 jobParams 冲突 | DSS params 覆盖 |
| 变量数量超 50 | 前端阻止添加 |
| 变量 value 为空字符串 | 允许，插值替换为空 |
| 变量 value 超长（>256） | 前端阻止 + 后端拒绝 |
| 任务 content 无任何 `${var}` | 正常执行，插值无副作用 |
| 变量值含日期表达式但格式错误 | `renderDt` 现有 try-catch 记录 ERROR，保留原样 |

---

**文档结束。核心结论：变量插值机制 80% 已端到端存在，P0 核心缺口是 DSS 动态传参注入（params 未合并到 jobParamsMap），P1 是校验与 UX 增强，P2 是类型/作用域/预览扩展。向后完全兼容。本功能为通用能力，可直接贡献开源社区。**

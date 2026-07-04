# REQ-01 文件数据集导入功能 — fieldMap 字段映射补充设计（开源版）

**适用仓库**: `../exchangis`（Exchangis Cluster 开源仓库）
**关联文档**: `REQ-01_文件数据集导入功能_设计_开源.md` v2.1（主设计）
**需求编号**: REQ-01
**版本归属**: Exchangis 1.1.18
**文档性质**: 给开源团队的**补充设计**——主设计 v2.1 已交付的上传/解析/BML 注入链路之外，**字段映射（fieldMap）** 这一步存在的缺口与方案
**作者侧**: 前端集成方（webank 内部 cluster 前端团队）

---

## 📋 执行摘要

REQ-01 文件 source 的**上传 / 流式解析 / BML 注入 / 作业保存**链路已由开源团队完成（commit fe7559f6b→d3f5dc28d→8d264a043→f8bc7eaaf），端到端可跑通：上传文件 → 解析 → `__file_bml_*` 进作业 source params → `FileDataxSubExchangisJobHandler` 读 BML 引用 → 引擎物化 `EngineBmlResource` → EC 下载到工作目录 → txtfilereader 读。

**但字段映射（fieldMap）这一步存在缺口**：前端 `getFields`（`/job/transform/settings`）链路耦合 source + sink，且要求 `sourceDataSourceId`。文件 source 按 M5/架构② **不创建数据源实例、无 `sourceDataSourceId`**，导致 `getFields` 链路在三个点被阻断，字段映射 UI 拿不到源/目的字段。

**本补充设计提出方案 A**：后端 `getFields` 链路新增 `sourceTypeId == "file"` 分支，源字段来自文件解析结果（前端随请求传 `sourceFileColumns`），目的字段仍按现有逻辑查 sink 数据源。改动聚焦在 3 个落点，对非 file 类型零影响。

---

## 1. 背景：为什么 fieldMap 这一步断了

### 1.1 文件 source 的 M5 约束（主设计已定）

按主设计 M5/架构②（开源版设计文档 §1.3、§4.3）：「文件」source **不创建数据源实例、不存数据源表、不进 `ExchangisDataSourceDefLoader` 加载范围**。文件 source 的作业配置 payload 为：

```json
{ "sourceType": "file", "resourceId": "...", "version": "...", "owner": "...", "name": "...", "path": "." }
```

**无 `dataSourceId`、无 `database`、无 `table`**。这是 M5 解耦的必然结果——文件不是"数据源"，是上传的临时数据集。

### 1.2 getFields 链路现状（前端 → 后端）

**前端**（`jobDetail.vue` / `index.vue`，cluster 前端 `../wedatasphere-exchangis-web/cluster`）：

```js
// getFieldsParams 返回的请求体（jobDetail.vue:861-885）
{
  sourceTypeId, sourceDataSourceId, sourceDataBase, sourceTable,
  sinkTypeId, sinkDataSourceId, sinkDataBase, sinkTable,
  engine, srcTblNotExist, sinkTblNotExist
}

// 调用前 gate（jobDetail.vue:871）
if (!source.type || !source.id || !sink.type || !sink.id) return null;
//   ↑ 文件 source 无 source.id → 永远 return null → getFields 永不调用
```

**后端**（开源 `../exchangis`）：

```
ExchangisJobTransformRestfulApi.settings(@Validated @RequestBody TransformRequestVo params)
  └─ DefaultJobTransformService.getSettings(params)
       ├─ TransformDefine transformDefine = transformDefineRulesFusion.fuse(sourceDefine, sinkDefine);
       │    ↑ 需要 source/sink 的 Definition（按 dataSourceType 查 transform 规则）
       └─ for each transform type:
            transformer.getSettings(requestVo)   // FieldMappingTransformer.getSettings
              ├─ getColumns(operator, sourceDataSourceId, sourceDataBase, sourceTable) → MetaColumn → FieldColumn
              ├─ settings.setSourceFields(sourceColumns)
              ├─ getColumns(operator, sinkDataSourceId, sinkDataBase, sinkTable) → FieldColumn
              └─ settings.setSinkFields(sinkColumns)
```

### 1.3 三处阻塞（文件 source）

| 编号 | 位置 | 阻塞点 | 文件 source 为何命中 |
|:----:|------|--------|---------------------|
| **G1** | `TransformRequestVo.java:23` | `@NotNull(message="source id cannot be null") private Long sourceDataSourceId;` | 文件 source 无 datasource id → **`@Validated` 在 controller 直接 400 拒绝**，请求进不了 service |
| **G2** | `DefaultJobTransformService.getSettings` → `transformDefineRulesFusion.fuse(sourceDefine, sinkDefine)` | 需按 source dataSourceType 查 `TransformDefine` 规则；source Define 缺失会让 fuse 找不到规则或抛错 | 文件 source 不注册 `ExchangisDataSourceDefinition`（M5/Q-CRIT），无 source Define |
| **G3** | `FieldMappingTransformer.getSettings:110-116` | `getColumns(operator, sourceDataSourceId, sourceDataBase, sourceTable)` 查源库表字段 | 文件 source 无 datasource id / 无库表 → 查询非法 |

**G1 是硬阻塞**（请求被验证层拒绝），即便绕过 G1，G2/G3 仍会让链路失败。三处必须联动改造。

---

## 2. 方案 A：后端 getFields 新增 file 分支

### 2.1 设计原则

- **file 分支只在 `sourceTypeId == "file"` 时触发**，非 file 类型（MySQL/Hive/...）走原路径，**零影响**
- **源字段来自文件解析结果**（`FileColumnDefine`，前端持有 `fileParseResult.columns`，已支持用户 UI 改类型 M4），不由后端查库
- **目的字段仍走原逻辑**（查 sink 数据源库表）——sink 一定是真实数据源（文件 source 仅作 source，作业校验拒绝 sink=file）
- **deductions（自动映射）后端照常算**——用文件源字段 × sink 字段做同名匹配，逻辑复用

### 2.2 契约变更：TransformRequestVo

**改动 1 — 放宽 `sourceDataSourceId` 的 `@NotNull`**：

```java
// ===== BEFORE =====
@NotNull(message = "source id cannot be null (来源数据源ID不能为空）")
private Long sourceDataSourceId;

// ===== AFTER =====
/**
 * Data source id (source direction).
 * ⚠️ Required for normal datasource types; OPTIONAL for sourceTypeId == "file"
 *    (file source has no datasource instance per M5).
 * 普通数据源类型必填；sourceTypeId == "file" 时无需（M5 解耦，文件不创建数据源实例）。
 */
private Long sourceDataSourceId;   // 去掉 @NotNull，改在 service 层条件校验
```

> 不要把 `@NotNull` 改成"全局可空"——那样非 file 类型丢失 sourceDataSourceId 会静默走到 NPE。**改为 service 层条件必填**（见 2.3 G1-service）。

**改动 2 — 新增文件 source 专用字段**：

```java
/**
 * File source columns (only used when sourceTypeId == "file").
 * Provided by frontend from the upload parse result (FileParseResult.columns),
 * possibly with user-edited inferred types (M4 "推断结果 UI 可见可改").
 *
 * 文件 source 列定义（仅 sourceTypeId == "file" 时使用）。
 * 由前端从上传解析结果携带（可能含用户在 UI 上修改过的推断类型 M4）。
 */
private List<FileColumnVo> sourceFileColumns;

/**
 * BML resource id of the uploaded file (optional, for audit/traceability when sourceTypeId == "file").
 * 文件 BML 资源 ID（sourceTypeId == "file" 时可选，用于审计/追溯）。
 */
private String sourceBmlResourceId;

/**
 * BML version of the uploaded file (optional, paired with sourceBmlResourceId).
 * 文件 BML 版本（与 sourceBmlResourceId 配对，可选）。
 */
private String sourceBmlVersion;
```

`FileColumnVo`（轻量，不复用 `FileColumnDefine` 以避免 parse 包与 render 包耦合）：

```java
public class FileColumnVo {
    private String name;         // 规范化列名 / normalized name
    private String type;         // 推断类型（int/long/double/decimal/date/timestamp/string）/ inferred type
    // getters/setters
}
```

### 2.3 三处落点改动（精确到类/方法）

#### G1-service：`DefaultJobTransformService.getSettings`（入口条件校验）

```java
public Map<String, TransformSettings> getSettings(TransformRequestVo requestVo) {
    // ⭐ 条件必填校验（替代原 @NotNull(sourceDataSourceId)）
    //   file source 不要求 sourceDataSourceId；非 file 类型仍要求
    //   Conditional required: file source doesn't need sourceDataSourceId; others still do.
    boolean isFileSource = "file".equalsIgnoreCase(requestVo.getSourceTypeId());
    if (!isFileSource && requestVo.getSourceDataSourceId() == null) {
        // 返回错误或抛 IllegalArgumentException（对齐既有 @NotNull 拒绝语义）
        throw new IllegalArgumentException("sourceDataSourceId cannot be null for non-file source (非文件来源 sourceDataSourceId 不能为空)");
    }
    // ... 既有逻辑
}
```

> G1 的 `@NotNull` 移除后，必填语义下沉到 service 层条件判断，行为对非 file 类型等价。

#### G2：`DefaultJobTransformService.getSettings` → `transformDefineRulesFusion.fuse`

`fuse(sourceDefine, sinkDefine)` 需要按 dataSourceType 取 transform 规则。文件 source 无 Define。两种处理方式（任选其一，推荐 G2-a）：

- **G2-a（推荐）**：`fuse` 内部对 `sourceTypeId == "file"` 特判——不查 source Define，直接用 sink Define + 一个固定的 file→mapping 规则返回 `TransformDefine`（含 field-mapping 类型）。文件 source 的 transform 需求与普通 source 一致（都是字段映射 + 处理器），仅字段来源不同。
- **G2-b**：`getSettings` 在调 fuse 前，若 `isFileSource`，跳过 fuse 直接 `settingsMap.put("field-mapping", fieldMappingTransformer.getSettings(requestVo))`。

```java
// G2-a 示意（TransformRulesFusionImpl 或 DefaultJobTransformService）
TransformDefine sourceDefine = isFileSource
    ? TransformDefine.fileSource()   // 静态工厂：返回文件 source 的 transform 类型集（field-mapping）
    : loadDefineByType(sourceTypeId);
TransformDefine transformDefine = transformDefineRulesFusion.fuse(sourceDefine, sinkDefine);
```

#### G3：`FieldMappingTransformer.getSettings`（字段查询分流）

```java
public TransformSettings getSettings(TransformRequestVo requestVo) {
    // ... 既有 operator 解析等

    // ===== 源字段：file 分流 =====
    List<FieldColumn> sourceColumns = new ArrayList<>();
    boolean isFileSource = "file".equalsIgnoreCase(requestVo.getSourceTypeId());
    if (isFileSource) {
        // ⭐ 文件 source：字段来自前端传入的 sourceFileColumns（尊重用户 UI 编辑 M4）
        //    File source: columns come from frontend-provided sourceFileColumns (respects M4 UI edits).
        List<FileColumnVo> fileCols = requestVo.getSourceFileColumns();
        if (fileCols != null) {
            boolean editable = true;   // 文件列字段名/类型均可在 UI 改
            for (int i = 0; i < fileCols.size(); i++) {
                FileColumnVo c = fileCols.get(i);
                sourceColumns.add(new FieldColumnWrapper(c.getName(), c.getType(), i, editable));
            }
        }
        // srcTblNotExist 对文件 source 不适用（文件必有表头/列）；不走 partition 分支
    } else {
        // 既有逻辑：查源库表 / Existing logic: query source db/table
        //   (原 line 110-132，含 partition 处理，保持不动)
        List<MetaColumn> metaColumns = getOrLoadMetadataInfoService()
                .getColumns(operator.get(), requestVo.getSourceDataSourceId(),
                        requestVo.getSourceDataBase(), requestVo.getSourceTable());
        // ... 既有 MetaColumn → FieldColumn + partition 逻辑
    }
    settings.setSourceFields(sourceColumns);

    // ===== 目的字段：不变（sink 必为真实数据源）/ Sink fields: unchanged =====
    List<FieldColumn> sinkColumns = new ArrayList<>();
    //   (原 line 136-153，查 sinkDataSourceId 库表，保持不动)
    settings.setSinkFields(sinkColumns);

    // ===== deductions / addEnable / transformEnable：既有自动映射逻辑保持不动 =====
    //   sourceColumns（文件列）× sinkColumns 同名匹配，复用既有 FieldColumnMatch 逻辑
    //   Auto name-match between file sourceColumns and sinkColumns, reuse existing FieldColumnMatch.
    return settings;
}
```

> **关键**：file 分支只替换 sourceColumns 的**来源**（前端传入 vs 查库），后续的 `setSinkFields` / deductions / 自动映射全部复用既有逻辑——改动面最小。

---

## 3. 请求/响应契约示例（文件 source）

### 3.1 请求

```
POST dss/exchangis/main/job/transform/settings
Content-Type: application/json

{
  "engine": "DATAX",
  "sourceTypeId": "file",
  "sourceFileColumns": [
    { "name": "id",   "type": "long" },
    { "name": "name", "type": "string" },
    { "name": "amount","type": "double" }
  ],
  "sourceBmlResourceId": "bml-xxx-yyy",
  "sourceBmlVersion": "v000001",
  "sinkTypeId": "MYSQL",
  "sinkDataSourceId": 123,
  "sinkDataBase": "ods",
  "sinkTable": "t_user",
  "sinkTblNotExist": false
}
```

> 文件 source 不传 `sourceDataSourceId` / `sourceDataBase` / `sourceTable`。

### 3.2 响应（与既有非 file 类型**完全一致**，前端零改动读取）

```json
{
  "types": ["field-mapping"],
  "sourceFields": [
    { "name": "id", "type": "long", "fieldIndex": 0, "fieldEditable": true },
    { "name": "name", "type": "string", "fieldIndex": 1, "fieldEditable": true },
    { "name": "amount", "type": "double", "fieldIndex": 2, "fieldEditable": true }
  ],
  "sinkFields": [
    { "name": "id", "type": "BIGINT", "fieldIndex": 0, "fieldEditable": false },
    { "name": "name", "type": "VARCHAR", "fieldIndex": 1, "fieldEditable": false }
  ],
  "deductions": [
    { "source": { "name": "id", "type": "long", ... }, "sink": { "name": "id", "type": "BIGINT", ... }, "deleteEnable": true },
    { "source": { "name": "name", "type": "string", ... }, "sink": { "name": "name", "type": "VARCHAR", ... }, "deleteEnable": true }
  ],
  "addEnable": true,
  "transformEnable": true
}
```

> `sourceFields` 来自前端 `sourceFileColumns`；`sinkFields` 来自后端查 sink 库表；`deductions` 后端同名匹配算出。响应结构 = 既有，前端 `res.sourceFields` / `res.sinkFields` / `res.deductions` 读取不变。

---

## 4. 方案选择：A1（前端传列，推荐） vs A2（后端查库）

源字段来源有两种可选实现：

| 维度 | **A1：前端传 sourceFileColumns（推荐）** | A2：后端按 resourceId+version 查 `exchangis_job_file_resources.parse_result` |
|------|----------------------------------------|----------------------------------------------------------------------|
| 字段来源 | 前端 `fileParseResult.columns`（上传解析结果，UI 可改类型 M4） | 后端 DB 里存的上传时 parse_result 快照 |
| **用户 UI 改类型是否生效** | ✅ 生效（前端传改后的） | ❌ 不生效（DB 存的是原始推断，除非每次改都重新上传落库） |
| 后端复杂度 | 低（直接读请求字段） | 中（需查 JobFileResourceDao + 反序列化 parse_result JSON） |
| 单一来源 | 前端为源 | DB 为源（更权威，可防篡改） |
| 审计/追溯 | 附 `sourceBmlResourceId`+`sourceBmlVersion` 可追溯 | resourceId+version 天然在 DB |

**推荐 A1**：M4 明确"推断结果 UI 可见可改"，用户在 `FileUploadComponent` 改了列类型后，字段映射应基于改后的类型。A2 会丢失用户编辑。**附 `sourceBmlResourceId`+`sourceBmlVersion`（可选）** 用于审计追溯即可。

若团队更倾向"后端权威、防前端篡改"，可走 A2，但需在 `FileUploadComponent` 类型修改后**触发重新落库 parse_result**（或新增一个 update 接口），否则 DB 与 UI 不一致。

---

## 5. 前端配套改动（参考，前端团队会改）

主设计文档不强制前端实现，但说明配套点（前端已就绪部分 + 待改部分）：

**已就绪**（前端已实现，本次无需改）：
- `FileUploadComponent.vue`：上传后持有 `fileParseResult.columns`（含用户可改的 `inferredType`）
- `dataSource.vue`：文件 source 落 `dataSourceIds.source = {type:'file', resourceId, version, owner, name, path}` + `dataSource.fileParseResult` 暂存解析结果
- `service.js`：`getFields` 已对接 `/job/transform/settings`

**待改（后端本设计落地后）**：
- `jobDetail.vue` / `index.vue` 的 `getFieldsParams(dataSource)`：
  - gate 放宽：`if (!source.type || (!source.id && source.type !== 'file') || !sink.type || !sink.id) return null;`
  - 构造分流：`source.type === 'file'` 时请求体改为 `{ sourceTypeId:'file', sourceFileColumns, sourceBmlResourceId, sourceBmlVersion, sink*, engine }`，不传 `sourceDataSourceId`/`sourceDataBase`/`sourceTable`
  - `sourceFileColumns` 取自 `dataSource.fileParseResult.columns` 映射 `{name, type: inferredType}`

```js
// 前端 getFieldsParams 文件分流示意
const source = dataSourceIds.source;
const isFile = source.type === 'file';
if (isFile) {
  return {
    engine: this.jobData.engineType,
    sourceTypeId: 'file',
    sourceFileColumns: (dataSource.fileParseResult?.columns || []).map(c => ({ name: c.name, type: c.inferredType })),
    sourceBmlResourceId: source.resourceId,
    sourceBmlVersion: source.version,
    sinkTypeId: dataSourceIds.sink.type,
    sinkDataSourceId: dataSourceIds.sink.id,
    sinkDataBase: dataSourceIds.sink.db,
    sinkTable: dataSourceIds.sink.table,
    sinkTblNotExist: !!dataSourceIds.sink.tableNotExist,
  };
}
// 非 file：既有逻辑
```

---

## 6. 影响面与兼容性

| 维度 | 影响 |
|------|------|
| **非 file 类型（MySQL/Hive/...）** | **零影响**。`sourceDataSourceId` 的 `@NotNull` 下沉到 service 条件判断（非 file 时仍必填，行为等价）；G2/G3 的 file 分支只在 `sourceTypeId=='file'` 触发 |
| **既有 `getSettings` 调用方** | 无。请求/响应结构对非 file 类型完全不变 |
| **`TransformRequestVo` 序列化** | 新增 3 个可选字段（`sourceFileColumns`/`sourceBmlResourceId`/`sourceBmlVersion`），旧前端不传时为 null，向后兼容 |
| **`FileColumnVo`** | 新增轻量 VO，仅 `getSettings` 文件分支用；不复用 parse 包的 `FileColumnDefine`，避免包耦合 |
| **作业保存/执行链路** | 不涉及（本设计只管字段映射 UI 的字段获取；保存的作业里 source params 的 `__file_bml_*` + transforms.mapping 已由主设计覆盖） |

---

## 7. 测试要点

| 用例 | 预期 |
|------|------|
| source=file + sink=MySQL，调 `/job/transform/settings` 传 `sourceFileColumns` | 200，`sourceFields` = 前端传入列，`sinkFields` = MySQL 库表字段，`deductions` = 同名匹配 |
| source=file，**不传** `sourceDataSourceId` | 200（不再被 `@NotNull` 拒绝） |
| source=MySQL，**不传** `sourceDataSourceId` | 400 / 错误（条件必填仍生效，等价原行为） |
| source=file，前端改了 `inferredType`（如 string→long） | `sourceFields[].type` 反映改动（A1 生效） |
| source=file + sinkTblNotExist=true | `sinkFields` 按 sourceColumns 自动建（既有"目的表不存在自动建列"逻辑应仍工作——验证 file 分支不破坏该 fallback） |
| 现有 MySQL→Hive、Hive→MySQL 等回归 | 字段映射结果与改造前一致（零回归） |

---

## 8. 改动清单（开源仓库 `../exchangis`）

| 文件 | 改动类型 | 说明 |
|------|:--------:|------|
| `render/transform/TransformRequestVo.java` | 修改 | 去掉 `sourceDataSourceId` 的 `@NotNull`；新增 `sourceFileColumns` / `sourceBmlResourceId` / `sourceBmlVersion` 字段 + getter/setter |
| `render/transform/FileColumnVo.java`（新增） | 新增 | 轻量列 VO `{name, type}` |
| `service/impl/DefaultJobTransformService.java` | 修改 | `getSettings` 入口加 `isFileSource` 条件必填校验（G1-service）；fuse 处 file 分支（G2） |
| `render/transform/*RulesFusion*` 或 fuse 实现 | 修改 | `sourceTypeId=='file'` 时跳过 source Define 查找，返回 file→mapping 的 `TransformDefine`（G2-a） |
| `render/transform/field/mapping/FieldMappingTransformer.java` | 修改 | `getSettings` source 字段计算加 `isFileSource` 分流：file 走 `sourceFileColumns` 映射，非 file 走原 `getColumns`（G3） |

**不涉及**：`ExchangisJobFileSourceRestfulApi`（上传/解析/删除，主设计已交付）、`FileDataxSubExchangisJobHandler`（BML 注入，主设计已交付）、前端（前端配套由前端团队改）。

---

## 9. 时序

1. 开源团队 review 本设计 → 确认 A1 + 落点
2. 后端实现 G1/G2/G3 + `FileColumnVo`
3. 后端提测（§7 用例）
4. 前端配套改 `getFieldsParams`（§5）
5. 联调：上传文件 → 选 sink → 打开 fieldMap → 看到源（文件列）+ 目的（库表）字段 + 自动映射 → 用户调整映射 → 保存作业 → 执行

# REQ-01 文件数据集导入功能 — params 重建补充设计（开源）

> 配套文档：`REQ-01_文件数据集导入功能_设计_开源.md`、`REQ-01_文件数据集导入功能_fieldMap字段映射补充设计_开源.md`
>
> 本文档针对 **文件 source 作业编辑态加载时 `params.sources` 丢失** 问题，定位后端根因并给出修复方案，供开源团队在 `../exchangis` 实施。

---

## 1. 问题现象

保存文件 source 作业后，再次编辑加载，前端从 `GET /dss/exchangis/main/job/{id}` 拿到的 `result.jobContent` 里 `subJobs[i].params.sources` 为**空数组**：

- 数据库 `exchangis_job_entity.job_content` 字段**含完整 params.sources**（前端保存时发的 saveContent，`config_key` 格式，含 `__file_bml_resource_id / __file_bml_version / __file_bml_owner / __file_bml_name` + `encoding / delimiter / nullFormat`）。
- 但接口返回的 jobContent 里 `params.sources` 为空 → 前端 dyncRender 无参数可渲染 → 文件 source 的编码/分隔符/空值格式 + BML 引用全部丢失，无法回显，用户改过的值也丢。

---

## 2. 根因定位

### 2.1 调用链

```
GET /dss/exchangis/main/job/{id}
  ExchangisJobRestfulApi.getJob()                                  (ExchangisJobRestfulApi.java:320)
    → jobInfoService.getDecoratedJob(request, id)                   ← "Decorated" = 装饰过，非原始 jobContent
        DefaultJobInfoService.getDecoratedJob()                     (DefaultJobInfoService.java:222)
          → uiGetter.getJobDataSourceUIs(request, id)               ← 用 UI 配置重建
              DefaultDataSourceService.getJobDataSourceUIs()        (DefaultDataSourceService.java:145)
                → buildAllUI(request, job, cnt)                     (AbstractDataSourceService.java:116)
                    → buildDataSourceParamsUI(dsIdsUI, content)
                        → buildDataSourceParamsFilledValueUI(paramConfigs, paramsList)
                            (AbstractDataSourceService.java:168)
          → jobVo.setJobContent(重建后的 content)                   (DefaultJobInfoService.java:234) ← 覆盖原始
```

### 2.2 致命点：`buildDataSourceParamsFilledValueUI` 完全依赖 `paramConfigs`

`AbstractDataSourceService.buildDataSourceParamsFilledValueUI`（`AbstractDataSourceService.java:168-187`）：

```java
protected List<ElementUI<?>> buildDataSourceParamsFilledValueUI(
        List<ExchangisJobParamConfig> paramConfigs,
        List<ExchangisJobParamsContent.ExchangisJobParamsItem> paramsList) {
    List<ElementUI<?>> uis = new ArrayList<>();
    if (!Objects.isNull(paramConfigs) && !paramConfigs.isEmpty()) {   // ← paramConfigs 来自 ExchangisJobParamConfig 表
        for (ExchangisJobParamConfig cfg : paramConfigs) {
            if (Objects.isNull(paramsList) || paramsList.isEmpty()) {
                uis.add(fillElementUIValue(cfg, ""));
                continue;
            }
            ExchangisJobParamsContent.ExchangisJobParamsItem selectedParamItem =
                    getJobParamsItem(cfg.getConfigKey(), paramsList);
            // ... 按 cfg 填值 ...
        }
    }
    return uis;   // paramConfigs 空 → 返回空 list
}
```

`paramConfigs` 由 `ExchangisJobParamConfigMapper` 按 source type 查 `exchangis_job_param_config` 表。

**FILE 类型在该表无注册**（M5 决策：文件 source 不创建 `ExchangisDataSourceDefinition`、不注册 param config），所以 `paramConfigs` 为空 → 重建后的 `params.sources` 为空。

### 2.3 `getDecoratedJob` 用重建结果覆盖原始 jobContent

`DefaultJobInfoService.getDecoratedJob`（`DefaultJobInfoService.java:222-243`）：

```java
List<ExchangisDataSourceUIViewer> jobDataSourceUIs = this.uiGetter.getJobDataSourceUIs(request, id);
ObjectMapper objectMapper = JsonUtils.jackson();
String content = objectMapper.writeValueAsString(jobDataSourceUIs);   // 重建后的（params.sources 空）
JsonNode contentJsonNode = objectMapper.readTree(content);
ObjectNode objectNode = objectMapper.createObjectNode();
objectNode.set("subJobs", contentJsonNode);
jobVo.setJobContent(objectNode.toString());                            // ← 覆盖原始 jobContent
```

因此接口返回的 jobContent `params.sources` 为空，而数据库原始 `job_content` 字段是完整的——"数据库有、接口没有"。

---

## 3. 为什么前端无法规避

前端 `getInfo` 只能拿到 `data.jobContent`（已被后端重建为空 params）。前端的 `restoreFileSourceParams`（`dataSource.vue`）从这个空 params 还原，自然拿不到值。即使从 `dataSourceIds.source` 拿 BML 引用调 `reparseFileSource` 重拉 parseResult，也只能拿原始解析值，**用户改过的 encoding/delimiter/nullFormat 会丢**。

**必须在后端修复**：重建时对文件 source 保留原始 params。

---

## 4. 修复方案

### 方案 A（推荐）：`buildAllUI` 对文件 source 透传原始 params

在 `AbstractDataSourceService.buildAllUI`（`AbstractDataSourceService.java:116-129`）里，**检测 source type == "FILE"**，不走 `ExchangisJobParamConfig` 表查询重建，**直接从原始 content 的 params.sources 透传**。

```java
protected ExchangisDataSourceUIViewer buildAllUI(HttpServletRequest request,
                                                 ExchangisJobEntity job,
                                                 ExchangisJobInfoContent content) {
    ExchangisDataSourceIdsUI dataSourceIdsUI = buildDataSourceIdsUI(request, content);

    // ⭐ REQ-01：文件 source 无 ExchangisJobParamConfig 注册（M5），表查询重建会返回空 list，
    //   导致 getDecoratedJob 用空 params 覆盖原始 jobContent。此处对 FILE 直接透传原始 params.sources
    //   （config_key 格式），前端 restoreFileSourceParams 已能处理 config_key 格式。
    //   File source has no param config registration (M5); table-based rebuild returns empty, which
    //   overwrites the original jobContent. Pass through original params.sources (config_key format)
    //   for FILE — frontend restoreFileSourceParams handles config_key format.
    ExchangisDataSourceParamsUI paramsUI;
    String sourceType = dataSourceIdsUI.getSource() == null ? null : dataSourceIdsUI.getSource().getType();
    if ("FILE".equalsIgnoreCase(sourceType)) {
        paramsUI = buildFileSourceParamsUIFromContent(content);
    } else {
        paramsUI = buildDataSourceParamsUI(dataSourceIdsUI, content);
    }

    ExchangisJobTransformsContent transforms = content.getTransforms();
    List<ElementUI<?>> jobDataSourceSettingsUI = this.buildJobSettingsUI(job.getEngineType(), content);
    return new DefaultDataSourceUIViewer(
            content.getSubJobName(), dataSourceIdsUI, paramsUI, transforms, jobDataSourceSettingsUI);
}
```

`buildFileSourceParamsUIFromContent(content)` 实现要点（二选一）：

- **简版（透传）**：把 `content.getSources()`（`List<ExchangisJobParamsContent.ExchangisJobParamsItem>`，含 `configKey / configName / configValue / sort`）转成 `List<ElementUI<?>>`（`InputElementUI`，`field=configKey, label=configName, value=configValue, sort=sort, show="_true"/"_false"`）。hidden 项（`__file_bml_*`）`show="_false"`。
- **复用版**：直接把 `content.getSources()` 包进 `ExchangisDataSourceParamsUI` 返回（前端已兼容 config_key 格式，无需转 UI）。

推荐简版（保持 viewer 输出结构一致：`params.sources` 仍是 `ElementUI` 列表，`__file_bml_*` hidden）。

### 方案 B（备选）：`getDecoratedJob` 对文件 source subJob 不重建

在 `DefaultJobInfoService.getDecoratedJob`（line 222-243），检测 subJob source 是否 FILE，若是则保留原始 jobContent（不调 `getJobDataSourceUIs` 重建）。粒度较粗（整个 subJob 不装饰），可能影响 `dataSourceIds / transforms` 的 UI 装饰，**不推荐**。

---

## 5. `dataSourceIds` 的 BML 字段（需同步确认）

`buildDataSourceIdsUI` → `parseDataSourceIdUi`（`AbstractDataSourceService.java:301-333`）用 `idValue.trim().split("\\.")` 拆 `type / id / db / table`：

```java
ExchangisDataSourceIdUI ui = new ExchangisDataSourceIdUI(jobDataSource);  // 从原始 content source 读
if (StringUtils.isNotBlank(idValue)) {
    String[] split = idValue.trim().split("\\.");
    ui.setType(split[0]);
    ui.setId(split[1]);
    ui.setDb(split[2]);
    ui.setTable(split[3]);
}
```

文件 source 的 `dataSourceIds.source` 是 `{type:'FILE', id:0, resourceId, version, owner, name, path}`，没有传统 `type.ds.db.table` 字符串。需确认：

1. `ExchangisDataSourceIdUI(jobDataSource)` 构造是否保留 `resourceId / version / owner / name / path`（BML 引用）—— 前端编辑态回显依赖这些。
2. `idValue` split 对 FILE type 是否会覆盖/污染 BML 字段。若会，对 `type == "FILE"` 跳过 split，保留 `jobDataSource` 原始字段。

---

## 6. 前端配合（已完成，无需开源团队改）

前端 `restoreFileSourceParams`（`wedatasphere-exchangis-web/cluster/src/pages/jobManagement/components/dataSource.vue`）已实现：

- 加载已保存作业时，把 `params.sources`（config_key 格式）重构为 UI 格式（`buildFileSourceParams`），值从 `config_value` 还原。
- emit `updateSourceParams` 同步 `curTask.params`，避免再次保存丢值。
- `saveAll` forEach 兜底兼容 config_key 格式项。

**前提**：接口返回的 `params.sources` 非空。本修复（方案 A）确保透传，前端即可正常回显。

---

## 7. 验证步骤

1. 前端新建文件 source 作业：上传 CSV/TXT → 改 encoding/delimiter/nullFormat → 选 sink → 字段映射 → 保存。
2. `GET /dss/exchangis/main/job/{id}` 返回的 `result.jobContent.subJobs[i].params.sources` 应含 7 项（`__file_bml_resource_id / version / owner / name` + `encoding / delimiter / nullFormat`，config_key 格式）。
3. 前端编辑态重新打开该作业：dyncRender 渲染 encoding/delimiter/nullFormat 输入框，值为用户保存值；FileUploadComponent 回显文件信息；字段映射正常。
4. 再次保存，参数不丢（前端 saveAll forEach 兜底 + curTask.params 已是 UI 格式）。

---

## 8. 涉及文件（开源仓库 `../exchangis`）

| 文件 | 方法 / 行号 | 改动 |
|---|---|---|
| `exchangis-datasource/exchangis-datasource-service/.../AbstractDataSourceService.java` | `buildAllUI` (116) | 加 FILE 分支，透传原始 params |
| 同上 | `parseDataSourceIdUi` (301) | 确认 FILE 的 BML 字段不被 split 覆盖 |
| 同上 | `buildDataSourceParamsFilledValueUI` (168) | 无需改（仅作为根因定位参照） |
| `exchangis-job/exchangis-job-server/.../DefaultJobInfoService.java` | `getDecoratedJob` (222) | 无需改（方案 A 在更上层拦截） |

---

## 9. 备注

- 本修复不影响非文件 source（mysql/hive 等）的既有重建逻辑——只在 `source.type == "FILE"` 时走透传分支。
- 文件 source 的 `__file_bml_*` 是 hidden 参数（`show="_false"`），不渲染但进入保存 payload，后端 handler 读取。重建时需保留这些项（不能因为 hidden 就丢）。

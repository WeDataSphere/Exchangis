package com.webank.wedatasphere.exchangis.job.server.render.transform;


import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TransformRequestVo {
    /**
     * Engine type
     */
    private String engine;

    /**
     * Source type
     */
    @NotNull(message = "source type cannot be null (来源类型不能为空)")
    private String sourceTypeId;

    /**
     * Data source id (source direction).
     * ⚠️ Required for normal datasource types; OPTIONAL for sourceTypeId == "file"
     *    (file source has no datasource instance per M5).
     * 普通数据源类型必填；sourceTypeId == "file" 时无需（M5 解耦，文件不创建数据源实例）。
     */
    private Long sourceDataSourceId;
    /**
     * Database (source direction)
     */
    private String sourceDataBase;

    /**
     * Table (source direction)
     */
    private String sourceTable;

    /**
     * Table (source) not exist
     */
    private boolean srcTblNotExist = false;

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

    /**
     * Sink type id
     */
    @NotNull(message = "sink type cannot be null (目的类型不能为空）")
    private String sinkTypeId;

    /**
     * Sink data source id
     */
    @NotNull(message = "sink id cannot be null (目的数据源ID不能为空）")
    private Long sinkDataSourceId;

    /**
     * Database (sink direction)
     */
    private String sinkDataBase;

    /**
     * Table (sink direction)
     */
    private String sinkTable;

    /**
     * Table (sink) not exist
      */
    private boolean sinkTblNotExist = false;
    /**
     * Labels
     */
    private Map<String, Object> labels = new HashMap<>();

    /**
     * Operate user
     */
    private String operator;
    public String getEngine() {
        return engine;
    }

    public void setEngine(String engine) {
        this.engine = engine;
    }

    public String getSourceTypeId() {
        return sourceTypeId;
    }

    public void setSourceTypeId(String sourceTypeId) {
        this.sourceTypeId = sourceTypeId;
    }

    public Long getSourceDataSourceId() {
        return sourceDataSourceId;
    }

    public void setSourceDataSourceId(Long sourceDataSourceId) {
        this.sourceDataSourceId = sourceDataSourceId;
    }

    public String getSourceDataBase() {
        return sourceDataBase;
    }

    public void setSourceDataBase(String sourceDataBase) {
        this.sourceDataBase = sourceDataBase;
    }

    public String getSourceTable() {
        return sourceTable;
    }

    public void setSourceTable(String sourceTable) {
        this.sourceTable = sourceTable;
    }

    public String getSinkTypeId() {
        return sinkTypeId;
    }

    public void setSinkTypeId(String sinkTypeId) {
        this.sinkTypeId = sinkTypeId;
    }

    public Long getSinkDataSourceId() {
        return sinkDataSourceId;
    }

    public void setSinkDataSourceId(Long sinkDataSourceId) {
        this.sinkDataSourceId = sinkDataSourceId;
    }

    public String getSinkDataBase() {
        return sinkDataBase;
    }

    public void setSinkDataBase(String sinkDataBase) {
        this.sinkDataBase = sinkDataBase;
    }

    public String getSinkTable() {
        return sinkTable;
    }

    public void setSinkTable(String sinkTable) {
        this.sinkTable = sinkTable;
    }

    public Map<String, Object> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, Object> labels) {
        this.labels = labels;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public void setSrcTblNotExist(boolean srcTblNotExist) {
        this.srcTblNotExist = srcTblNotExist;
    }

    public void setSinkTblNotExist(boolean sinkTblNotExist) {
        this.sinkTblNotExist = sinkTblNotExist;
    }

    public boolean isSrcTblNotExist() {
        return srcTblNotExist;
    }

    public boolean isSinkTblNotExist() {
        return sinkTblNotExist;
    }

    public List<FileColumnVo> getSourceFileColumns() {
        return sourceFileColumns;
    }

    public void setSourceFileColumns(List<FileColumnVo> sourceFileColumns) {
        this.sourceFileColumns = sourceFileColumns;
    }

    public String getSourceBmlResourceId() {
        return sourceBmlResourceId;
    }

    public void setSourceBmlResourceId(String sourceBmlResourceId) {
        this.sourceBmlResourceId = sourceBmlResourceId;
    }

    public String getSourceBmlVersion() {
        return sourceBmlVersion;
    }

    public void setSourceBmlVersion(String sourceBmlVersion) {
        this.sourceBmlVersion = sourceBmlVersion;
    }
}

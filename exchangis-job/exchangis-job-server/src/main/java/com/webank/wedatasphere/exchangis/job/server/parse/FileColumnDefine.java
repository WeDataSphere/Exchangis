package com.webank.wedatasphere.exchangis.job.server.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * File column define (per-column metadata inferred from sampling).
 *
 * <p>Renamed from the design doc's "ColumnDefine" to avoid clashing with
 * {@code SubExchangisJob.ColumnDefine} (name/type semantics) — file parse columns are
 * a different concept.
 *
 * <p>文件列定义（采样推断的单列元信息）。改名自设计文档的 "ColumnDefine"，
 * 避免与 {@code SubExchangisJob.ColumnDefine} 语义冲突。
 */
public class FileColumnDefine {

    /**
     * Normalized column name (trim / illegal char to underscore / dedup suffix / keep Chinese)
     * 规范化后列名（去空格/非法字符转下划线/重名加序号/中文保留）
     */
    private String name;

    /**
     * Original column name (not normalized) / 原始列名
     */
    private String originalName;

    /**
     * Inferred type: int/long/double/decimal/date/timestamp/string / 推断类型
     */
    private String inferredType;

    /**
     * Sample values (first few) / 采样值前几条
     */
    private List<String> sampleValues = new ArrayList<>();

    /**
     * Null rate (nullCount / sampledRows) / 空值率
     */
    private double nullRate;

    public FileColumnDefine() {
    }

    public FileColumnDefine(String name, String originalName, String inferredType) {
        this.name = name;
        this.originalName = originalName;
        this.inferredType = inferredType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public String getInferredType() {
        return inferredType;
    }

    public void setInferredType(String inferredType) {
        this.inferredType = inferredType;
    }

    public List<String> getSampleValues() {
        return sampleValues;
    }

    public void setSampleValues(List<String> sampleValues) {
        this.sampleValues = sampleValues;
    }

    public double getNullRate() {
        return nullRate;
    }

    public void setNullRate(double nullRate) {
        this.nullRate = nullRate;
    }
}

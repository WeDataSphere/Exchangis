package com.webank.wedatasphere.exchangis.job.server.domain;

import java.util.Date;

/**
 * File source BML resource metadata entity (mirrors {@code exchangis_job_file_resources} table).
 *
 * <p>Stores the BML reference of a file uploaded as a job source, for metadata/reuse/audit.
 * Architecture ② / M5: file source does NOT go through datasource management, so this table is
 * the only place holding the file BML reference (no {@code exchangis_datasource} row).
 *
 * <p>文件 source 上传至 BML 的文件元数据实体（对应 {@code exchangis_job_file_resources} 表）。
 * 架构②/M5：文件 source 不走数据源管理流程，本表是文件 BML 引用的唯一存储（不写数据源表）。
 */
public class JobFileResource {

    /**
     * Id
     */
    private Long id;

    /**
     * Associated job id, null if uploaded before job save
     * 关联作业ID，上传时未保存作业则为空
     */
    private Long jobId;

    /**
     * Original file name / 原始文件名
     */
    private String fileName;

    /**
     * File size in bytes / 文件字节数
     */
    private long fileSize;

    /**
     * File type: CSV/TEXT / 文件类型
     */
    private String fileType;

    /**
     * Detected encoding / 检测编码
     */
    private String encoding;

    /**
     * Detected separator / 检测分隔符
     */
    private String separator;

    /**
     * Has header row / 是否有表头
     */
    private boolean hasHeader;

    /**
     * BML resource id / BML 资源ID
     */
    private String bmlResourceId;

    /**
     * BML version / BML 版本
     */
    private String bmlVersion;

    /**
     * BML owner/creator / BML 所有者/创建者
     */
    private String owner;

    /**
     * FileParseResult JSON snapshot / 解析结果 JSON 快照
     */
    private String parseResult;

    /**
     * Create time / 创建时间
     */
    private Date createTime;

    /**
     * Update time / 更新时间
     */
    private Date updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getSeparator() {
        return separator;
    }

    public void setSeparator(String separator) {
        this.separator = separator;
    }

    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public String getBmlResourceId() {
        return bmlResourceId;
    }

    public void setBmlResourceId(String bmlResourceId) {
        this.bmlResourceId = bmlResourceId;
    }

    public String getBmlVersion() {
        return bmlVersion;
    }

    public void setBmlVersion(String bmlVersion) {
        this.bmlVersion = bmlVersion;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public String getParseResult() {
        return parseResult;
    }

    public void setParseResult(String parseResult) {
        this.parseResult = parseResult;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }
}

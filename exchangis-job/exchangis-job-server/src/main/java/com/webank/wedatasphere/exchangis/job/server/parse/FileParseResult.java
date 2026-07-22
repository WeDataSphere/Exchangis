package com.webank.wedatasphere.exchangis.job.server.parse;

import java.util.ArrayList;
import java.util.List;

/**
 * File parse result (output of the parse stage, returned to frontend in the upload response).
 *
 * <p>文件解析结果 DTO（解析侧输出，随上传接口响应返回前端）。
 */
public class FileParseResult {

    /**
     * File name / 文件名
     */
    private String fileName;

    /**
     * File size in bytes / 文件字节数
     */
    private long fileSize;

    /**
     * Detected encoding (UTF-8/GBK/...) / 检测出的编码
     */
    private String encoding;

    /**
     * Has BOM header / 是否有 BOM 头
     */
    private boolean hasBom;

    /**
     * Separator (,/\t/;/|) / 分隔符
     */
    private Character separator;

    /**
     * Quote char (null if none) / 引号字符（无则 null）
     */
    private Character quoteChar;

    /**
     * File format for DataX txtfilereader (Key.FILE_FORMAT): "csv" (OpenCSV CsvReader,
     * handles quoted fields per RFC 4180) or "text" (plain delimiter split). Returned to
     * the frontend so the sync-task submission can forward it to txtfilereader; otherwise
     * txtfilereader always defaults to "csv" and mis-parses plain-text (.txt) files.
     * 文件格式，对应 DataX txtfilereader（Key.FILE_FORMAT）：csv（OpenCSV CsvReader，按 RFC 4180
     * 处理引号字段）或 text（简单分隔符切分）。随响应返回前端，供提交同步任务时透传给 txtfilereader，
     * 否则 txtfilereader 恒默认 csv，会误解析纯文本(.txt)文件。
     */
    private String fileFormat;

    /**
     * First row is header / 首行是否表头
     */
    private boolean hasHeader;

    /**
     * Raw header row string (not normalized) / 原始首行字符串
     */
    private String headerRow;

    /**
     * Column defines (normalized name + inferred type) / 列定义
     */
    private List<FileColumnDefine> columns = new ArrayList<>();

    /**
     * Actual sampled rows (may be &lt;100) / 实际采样行数
     */
    private int sampledRowCount;

    /**
     * Error code (null on success) / 解析错误码（成功则 null）
     */
    private String errorCode;

    /**
     * Error message / 解析错误消息
     */
    private String errorMsg;

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

    public String getEncoding() {
        return encoding;
    }

    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public boolean isHasBom() {
        return hasBom;
    }

    public void setHasBom(boolean hasBom) {
        this.hasBom = hasBom;
    }

    public Character getSeparator() {
        return separator;
    }

    public void setSeparator(Character separator) {
        this.separator = separator;
    }

    public Character getQuoteChar() {
        return quoteChar;
    }

    public void setQuoteChar(Character quoteChar) {
        this.quoteChar = quoteChar;
    }

    public String getFileFormat() {
        return fileFormat;
    }

    public void setFileFormat(String fileFormat) {
        this.fileFormat = fileFormat;
    }

    public boolean isHasHeader() {
        return hasHeader;
    }

    public void setHasHeader(boolean hasHeader) {
        this.hasHeader = hasHeader;
    }

    public String getHeaderRow() {
        return headerRow;
    }

    public void setHeaderRow(String headerRow) {
        this.headerRow = headerRow;
    }

    public List<FileColumnDefine> getColumns() {
        return columns;
    }

    public void setColumns(List<FileColumnDefine> columns) {
        this.columns = columns;
    }

    public int getSampledRowCount() {
        return sampledRowCount;
    }

    public void setSampledRowCount(int sampledRowCount) {
        this.sampledRowCount = sampledRowCount;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}

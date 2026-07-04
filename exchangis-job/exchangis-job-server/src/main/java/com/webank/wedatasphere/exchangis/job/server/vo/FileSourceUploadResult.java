package com.webank.wedatasphere.exchangis.job.server.vo;

import com.webank.wedatasphere.exchangis.job.server.parse.FileParseResult;

/**
 * File source upload result (上传接口响应体)
 *
 * <p>M4: the upload response synchronously returns the BML resourceId + version and the
 * full {@link FileParseResult}.
 *
 * <p>M4：上传接口响应体同步返回 BML resourceId+version 与完整解析结果。
 */
public class FileSourceUploadResult {

    /**
     * BML resource id
     */
    private String resourceId;

    /**
     * BML version
     */
    private String version;

    /**
     * BML owner/creator (the uploading user, i.e. the current login user).
     * Frontend needs this to build the {@code __file_bml_owner} job source param.
     *
     * BML 所有者/创建者（上传用户，即当前登录用户）。
     * 前端据此构造作业 source 参数 {@code __file_bml_owner}。
     */
    private String owner;

    /**
     * File parse result (解析结果)
     */
    private FileParseResult fileParseResult;

    public String getResourceId() {
        return resourceId;
    }

    public void setResourceId(String resourceId) {
        this.resourceId = resourceId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }

    public FileParseResult getFileParseResult() {
        return fileParseResult;
    }

    public void setFileParseResult(FileParseResult fileParseResult) {
        this.fileParseResult = fileParseResult;
    }
}

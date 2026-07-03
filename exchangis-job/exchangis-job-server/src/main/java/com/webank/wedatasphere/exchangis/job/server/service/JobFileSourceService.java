package com.webank.wedatasphere.exchangis.job.server.service;

import com.webank.wedatasphere.exchangis.job.server.parse.FileParseResult;
import com.webank.wedatasphere.exchangis.job.server.vo.FileSourceUploadResult;
import org.springframework.web.multipart.MultipartFile;

/**
 * File source service (文件 source 上传/解析/删除服务)
 *
 * <p>Architecture ② / M5: file source does NOT go through datasource management; this service
 * owns the upload → streaming parse → BML upload → metadata persist flow.
 *
 * <p>架构②/M5：文件 source 不走数据源管理流程；本服务负责 上传→流式解析→BML 上传→元数据落库 链路。
 */
public interface JobFileSourceService {

    /**
     * Upload and parse a file source (上传并流式解析文件 source)
     *
     * <p>Flow (M3''): read the first N bytes (bounded) into memory and parse header + sample rows
     * (fail fast BEFORE BML upload if the header is bad — no orphan HDFS bytes); then stream
     * [buffered bytes] + [remaining upstream] to BML via the existing {@code BmlClients}; persist
     * the BML reference to {@code exchangis_job_file_resources}; return resourceId + version +
     * parse result synchronously (M4).
     *
     * @param operator      operator (login user) / 操作人
     * @param fileName      file name / 文件名
     * @param multipartFile uploaded multipart file / 上传的文件
     * @param jobId         optional job id (null if uploaded before job save) / 可选作业ID
     * @return upload result (resourceId + version + parse result)
     */
    FileSourceUploadResult uploadAndParse(String operator, String fileName,
                                          MultipartFile multipartFile, Long jobId);

    /**
     * Re-parse an already uploaded BML resource (重解析：从 BML 流式拉取重新解析)
     *
     * @param operator operator
     * @param resourceId BML resource id
     * @param version    BML version
     * @return parse result
     */
    FileParseResult reparse(String operator, String resourceId, String version);

    /**
     * Delete a file source BML resource record (删除文件 source 记录)
     *
     * <p>Note: BML server-side cleanup is best-effort (the BML clean-up task is disabled upstream);
     * this method removes the local {@code exchangis_job_file_resources} row.
     *
     * @param operator  operator
     * @param resourceId BML resource id
     * @param jobId      optional job id (for cascading delete by job)
     */
    void delete(String operator, String resourceId, Long jobId);
}

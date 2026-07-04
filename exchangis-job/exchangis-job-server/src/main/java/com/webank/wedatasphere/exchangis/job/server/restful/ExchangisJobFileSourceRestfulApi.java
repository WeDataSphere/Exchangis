package com.webank.wedatasphere.exchangis.job.server.restful;

import com.webank.wedatasphere.exchangis.common.UserUtils;
import com.webank.wedatasphere.exchangis.datasource.core.utils.Json;
import com.webank.wedatasphere.exchangis.job.server.parse.FileParseResult;
import com.webank.wedatasphere.exchangis.job.server.parse.FileSourceParseException;
import com.webank.wedatasphere.exchangis.job.server.service.JobFileSourceService;
import com.webank.wedatasphere.exchangis.job.server.vo.FileSourceUploadResult;
import org.apache.linkis.server.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;

/**
 * File source restful api (文件 source 上传/解析/删除接口)
 *
 * <p>Architecture ② / M5: file source does NOT go through datasource management. This controller
 * is the only entry for file source upload/parse/delete; datasource management APIs are unaware
 * of the "file" type.
 *
 * <p>Class-level URI aligns with {@code ExchangisJobTransformRestfulApi} (job scope, DSS AppConn
 * namespace). 架构②/M5：文件 source 不走数据源管理，本接口为唯一入口。
 */
@RestController
@RequestMapping(value = "dss/exchangis/main/job/fileSource", produces = {"application/json;charset=utf-8"})
public class ExchangisJobFileSourceRestfulApi {

    private static final Logger LOG = LoggerFactory.getLogger(ExchangisJobFileSourceRestfulApi.class);

    @Resource
    private JobFileSourceService jobFileSourceService;

    /**
     * Upload and parse a file source (上传并流式解析文件 source)
     *
     * <p>M3''/M4: streaming parse while uploading; the response synchronously returns the BML
     * resourceId + version and the full {@link FileParseResult}.
     *
     * @param file        uploaded file (CSV/TEXT)
     * @param fileName    file name (optional, falls back to the multipart original name)
     * @param jobId       optional job id (if uploaded from a saved job)
     * @param request     http request
     * @return message with resourceId / version / fileParseResult
     */
    @RequestMapping(value = "/upload", method = RequestMethod.POST)
    public Message upload(@RequestParam("file") MultipartFile file,
                          @RequestParam(value = "fileName", required = false) String fileName,
                          @RequestParam(value = "jobId", required = false) Long jobId,
                          HttpServletRequest request) {
        String userName = UserUtils.getLoginUser(request);
        Message response = Message.ok();
        try {
            FileSourceUploadResult result = jobFileSourceService.uploadAndParse(userName, fileName, file, jobId);
            response.data("resourceId", result.getResourceId());
            response.data("version", result.getVersion());
            response.data("owner", result.getOwner());
            response.data("fileParseResult", Json.convert(result.getFileParseResult(), Map.class, String.class, Object.class));
        } catch (FileSourceParseException e) {
            // Fail fast (编码/格式/文件头异常): return a structured error with the error code.
            LOG.warn("File source upload fail-fast (上传 fail fast): user={}, fileName={}, code={}, msg={}",
                    userName, fileName, e.getErrorCode(), e.getMessage());
            response = Message.error(e.getMessage()).data("errorCode", e.getErrorCode());
        } catch (Exception e) {
            LOG.error("File source upload failed (文件 source 上传失败): user={}, fileName={}",
                    userName, fileName, e);
            response = Message.error("File source upload failed (文件 source 上传失败): " + e.getMessage());
        }
        return response;
    }

    /**
     * Re-parse an already uploaded BML resource (重解析：不重传文件，从 BML 拉取重新解析)
     *
     * @param resourceId BML resource id
     * @param version    BML version
     * @param request    http request
     * @return message with fileParseResult
     */
    @RequestMapping(value = "/parse", method = RequestMethod.POST)
    public Message parse(@RequestParam("resourceId") String resourceId,
                         @RequestParam("version") String version,
                         HttpServletRequest request) {
        String userName = UserUtils.getLoginUser(request);
        Message response = Message.ok();
        try {
            FileParseResult parseResult = jobFileSourceService.reparse(userName, resourceId, version);
            response.data("fileParseResult", Json.convert(parseResult, Map.class, String.class, Object.class));
        } catch (FileSourceParseException e) {
            LOG.warn("File source reparse fail-fast (重解析 fail fast): user={}, resourceId={}, code={}, msg={}",
                    userName, resourceId, e.getErrorCode(), e.getMessage());
            response = Message.error(e.getMessage()).data("errorCode", e.getErrorCode());
        } catch (Exception e) {
            LOG.error("File source reparse failed (文件 source 重解析失败): user={}, resourceId={}",
                    userName, resourceId, e);
            response = Message.error("File source reparse failed (文件 source 重解析失败): " + e.getMessage());
        }
        return response;
    }

    /**
     * Delete a file source BML resource (删除文件 source BML 资源)
     *
     * <p>BML server-side cleanup is best-effort; the local metadata row is removed.
     *
     * @param resourceId BML resource id
     * @param version    BML version
     * @param jobId      optional job id (for cascading delete by job)
     * @param request    http request
     * @return message
     */
    @RequestMapping(value = "/{resourceId}/versions/{version}", method = RequestMethod.DELETE)
    public Message delete(@PathVariable("resourceId") String resourceId,
                          @PathVariable("version") String version,
                          @RequestParam(value = "jobId", required = false) Long jobId,
                          HttpServletRequest request) {
        String userName = UserUtils.getLoginUser(request);
        Message response = Message.ok();
        try {
            Objects.requireNonNull(resourceId, "resourceId cannot be null (resourceId 不能为空)");
            jobFileSourceService.delete(userName, resourceId, jobId);
            response.data("resourceId", resourceId);
        } catch (Exception e) {
            LOG.error("File source delete failed (文件 source 删除失败): user={}, resourceId={}",
                    userName, resourceId, e);
            response = Message.error("File source delete failed (文件 source 删除失败): " + e.getMessage());
        }
        return response;
    }
}

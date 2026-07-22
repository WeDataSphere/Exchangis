package com.webank.wedatasphere.exchangis.job.server.service.impl;

import com.webank.wedatasphere.exchangis.datasource.core.utils.Json;
import com.webank.wedatasphere.exchangis.engine.resource.bml.BmlClients;
import com.webank.wedatasphere.exchangis.job.server.configuration.FileSourceConfiguration;
import com.webank.wedatasphere.exchangis.job.server.domain.JobFileResource;
import com.webank.wedatasphere.exchangis.job.server.mapper.JobFileResourceDao;
import com.webank.wedatasphere.exchangis.job.server.parse.FileParseResult;
import com.webank.wedatasphere.exchangis.job.server.parse.FileSourceParseException;
import com.webank.wedatasphere.exchangis.job.server.parse.StreamFileHeaderParser;
import com.webank.wedatasphere.exchangis.job.server.service.JobFileSourceService;
import com.webank.wedatasphere.exchangis.job.server.vo.FileSourceUploadResult;
import org.apache.commons.io.IOUtils;
import org.apache.linkis.bml.protocol.BmlDownloadResponse;
import org.apache.linkis.bml.protocol.BmlUploadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.util.Locale;
import java.util.Objects;

/**
 * File source service implementation (文件 source 服务实现)
 *
 * <p>M3'': bounded-buffer parse-then-upload — read the first {@code parse-buffer-bytes} into memory
 * and parse (fail fast BEFORE BML upload if the header is bad, so no orphan HDFS bytes); then stream
 * [buffered bytes] + [remaining upstream] to BML via the existing {@link BmlClients}. The full file is
 * never buffered in Exchangis memory and never written to an Exchangis temp file.
 *
 * <p>M3'': 有界缓冲「先解析后上传」——先读前 N 字节入内存解析（文件头异常即 fail fast，不上传 BML，
 * 无孤儿字节）；再将 [缓冲字节]+[剩余上游流] 流式透传给 BML。Exchangis 端不全量缓冲、不落临时文件。
 */
@Service
public class JobFileSourceServiceImpl implements JobFileSourceService {

    private static final Logger LOG = LoggerFactory.getLogger(JobFileSourceServiceImpl.class);

    @Resource
    private JobFileResourceDao jobFileResourceDao;

    /**
     * Stateless parser instance (无状态解析器)
     */
    private final StreamFileHeaderParser parser = new StreamFileHeaderParser();

    @Override
    public FileSourceUploadResult uploadAndParse(String operator, String fileName,
                                                 MultipartFile multipartFile, Long jobId) {
        Objects.requireNonNull(operator, "operator cannot be null (操作人不能为空)");
        Objects.requireNonNull(multipartFile, "multipartFile cannot be null (上传文件不能为空)");
        String resolvedFileName = resolveFileName(fileName, multipartFile);
        long fileSize = multipartFile.getSize();
        long maxFileSize = FileSourceConfiguration.MAX_FILE_SIZE.getValue();
        if (fileSize > maxFileSize) {
            throw new FileSourceParseException("FILE_TOO_LARGE",
                    "File size " + fileSize + " exceeds the limit " + maxFileSize
                            + " (文件大小超过上限)");
        }

        int parseBuffer = FileSourceConfiguration.PARSE_BUFFER_BYTES.getValue();
        int sampleRows = FileSourceConfiguration.SAMPLE_ROWS.getValue();
        int headerRows = FileSourceConfiguration.HEADER_ROWS.getValue();

        InputStream multipartStream = null;
        try {
            multipartStream = multipartFile.getInputStream();
            // 1. Read the first N bytes (bounded) into memory (读前 N 字节入内存)
            byte[] buffer = new byte[parseBuffer];
            int read = readFully(multipartStream, buffer);

            // 2. Parse header + sample rows (fail fast BEFORE BML upload) (解析+fail fast)
            FileParseResult parseResult = parser.parse(buffer, read, resolvedFileName,
                    fileSize, sampleRows, headerRows);
            if (parseResult.getErrorCode() != null) {
                // Fail fast: do NOT upload to BML — close the stream and abort.
                // fail fast：不上传 BML，关闭流并中止（无孤儿字节）
                IOUtils.closeQuietly(multipartStream);
                throw new FileSourceParseException(parseResult.getErrorCode(), parseResult.getErrorMsg());
            }

            // 3. Stream [buffered bytes] + [remaining upstream] to BML (流式透传给 BML)
            //    multipartStream is now positioned right after the buffered bytes, so the
            //    sequence stream reconstructs the full file content for BML.
            //    multipartStream 已读到缓冲字节之后，拼接后即为完整文件内容
            InputStream fullStream = new SequenceInputStream(
                    new ByteArrayInputStream(buffer, 0, read), multipartStream);
            BmlUploadResponse uploadResponse;
            try {
                uploadResponse = BmlClients.getInstance().uploadResource(operator, resolvedFileName, fullStream);
            } catch (Exception e) {
                LOG.error("Fail to upload file source to BML (上传文件至 BML 失败): file={}, user={}",
                        resolvedFileName, operator, e);
                throw new FileSourceParseException("BML_UPLOAD_FAILED",
                        "Fail to upload file to BML (上传文件至 BML 失败): " + e.getMessage(), e);
            } finally {
                IOUtils.closeQuietly(fullStream);
            }
            if (Objects.isNull(uploadResponse) || Objects.isNull(uploadResponse.resourceId())) {
                throw new FileSourceParseException("BML_UPLOAD_FAILED",
                        "BML upload response is empty (BML 上传响应为空)");
            }
            String resourceId = uploadResponse.resourceId();
            String version = uploadResponse.version();
            LOG.info("File source uploaded to BML (文件已上传至 BML): file={}, user={}, resourceId={}, version={}",
                    resolvedFileName, operator, resourceId, version);

            // 4. Persist BML reference to exchangis_job_file_resources (落库元数据)
            JobFileResource entity = new JobFileResource();
            entity.setJobId(jobId);
            entity.setFileName(resolvedFileName);
            entity.setFileSize(fileSize);
            // file_type derives from the parse result's fileFormat (single source of truth
            // in StreamFileHeaderParser), uppercased to the DB convention CSV/TEXT.
            // file_type 由解析结果的 fileFormat（StreamFileHeaderParser 为唯一来源）大写派生为 DB 约定 CSV/TEXT。
            String fileFormat = parseResult.getFileFormat();
            entity.setFileType(fileFormat != null ? fileFormat.toUpperCase(Locale.ROOT) : "CSV");
            entity.setEncoding(parseResult.getEncoding());
            entity.setSeparator(parseResult.getSeparator() == null ? null : String.valueOf(parseResult.getSeparator()));
            entity.setHasHeader(parseResult.isHasHeader());
            entity.setBmlResourceId(resourceId);
            entity.setBmlVersion(version);
            entity.setOwner(operator);
            entity.setParseResult(Json.toJson(parseResult, null));
            try {
                jobFileResourceDao.insert(entity);
            } catch (Exception e) {
                // DB failure should not fail the whole upload (BML already has the file).
                // 落库失败不影响上传结果（BML 已存文件），仅记录日志
                LOG.warn("Fail to persist file source metadata (文件元数据落库失败): file={}, resourceId={}",
                        resolvedFileName, resourceId, e);
            }

            // 5. Return synchronously (M4) (同步返回)
            FileSourceUploadResult result = new FileSourceUploadResult();
            result.setResourceId(resourceId);
            result.setVersion(version);
            // owner = operator (the uploading user); BML records it as the resource owner
            // owner = operator（上传用户），BML 以此作为资源所有者
            result.setOwner(operator);
            result.setFileParseResult(parseResult);
            return result;
        } catch (IOException e) {
            IOUtils.closeQuietly(multipartStream);
            throw new FileSourceParseException("IO_ERROR",
                    "Fail to read upload stream (读取上传流失败): " + e.getMessage(), e);
        }
    }

    @Override
    public FileParseResult reparse(String operator, String resourceId, String version) {
        Objects.requireNonNull(operator, "operator cannot be null (操作人不能为空)");
        Objects.requireNonNull(resourceId, "resourceId cannot be null (resourceId 不能为空)");
        Objects.requireNonNull(version, "version cannot be null (version 不能为空)");

        BmlDownloadResponse downloadResponse;
        try {
            downloadResponse = BmlClients.getInstance().downloadResource(operator, resourceId, version);
        } catch (Exception e) {
            throw new FileSourceParseException("BML_DOWNLOAD_FAILED",
                    "Fail to download from BML (从 BML 下载失败): " + e.getMessage(), e);
        }
        if (Objects.isNull(downloadResponse) || Objects.isNull(downloadResponse.inputStream())) {
            throw new FileSourceParseException("BML_DOWNLOAD_FAILED",
                    "BML download response is empty (BML 下载响应为空)");
        }
        int parseBuffer = FileSourceConfiguration.PARSE_BUFFER_BYTES.getValue();
        int sampleRows = FileSourceConfiguration.SAMPLE_ROWS.getValue();
        int headerRows = FileSourceConfiguration.HEADER_ROWS.getValue();
        String fileName = resourceId;
        // Try to fetch the stored file name from the DB for a better result (尝试从库中取文件名)
        JobFileResource stored = null;
        try {
            stored = jobFileResourceDao.getByResourceId(resourceId);
        } catch (Exception e) {
            LOG.warn("Fail to fetch stored file metadata (查询文件元数据失败): resourceId={}", resourceId, e);
        }
        if (Objects.nonNull(stored) && Objects.nonNull(stored.getFileName())) {
            fileName = stored.getFileName();
        }
        try (InputStream in = downloadResponse.inputStream()) {
            byte[] buffer = new byte[parseBuffer];
            int read = readFully(in, buffer);
            long fileSize = Objects.nonNull(stored) ? stored.getFileSize() : (long) read;
            FileParseResult parseResult = parser.parse(buffer, read, fileName, fileSize, sampleRows, headerRows);
            if (parseResult.getErrorCode() != null) {
                throw new FileSourceParseException(parseResult.getErrorCode(), parseResult.getErrorMsg());
            }
            return parseResult;
        } catch (IOException e) {
            throw new FileSourceParseException("IO_ERROR",
                    "Fail to read download stream (读取下载流失败): " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String operator, String resourceId, Long jobId) {
        // BML server-side cleanup is best-effort (BML clean-up task is disabled upstream);
        // here we remove the local metadata row.
        // BML 侧清理为尽力而为（上游清理任务已禁用），此处仅删除本地元数据记录
        try {
            if (Objects.nonNull(jobId)) {
                jobFileResourceDao.deleteByJobId(jobId);
                LOG.info("File source metadata deleted by job (按作业删除文件元数据): jobId={}", jobId);
            } else if (Objects.nonNull(resourceId)) {
                JobFileResource stored = jobFileResourceDao.getByResourceId(resourceId);
                if (Objects.nonNull(stored)) {
                    jobFileResourceDao.deleteByJobId(stored.getJobId());
                    LOG.info("File source metadata deleted by resourceId (按 resourceId 删除文件元数据): resourceId={}",
                            resourceId);
                }
            }
        } catch (Exception e) {
            LOG.warn("Fail to delete file source metadata (删除文件元数据失败): resourceId={}, jobId={}",
                    resourceId, jobId, e);
        }
    }

    /**
     * Read up to {@code buffer.length} bytes from the stream; return the number of bytes read
     * (may be less than buffer length if EOF is reached).
     */
    private int readFully(InputStream in, byte[] buffer) throws IOException {
        int total = 0;
        int n;
        while (total < buffer.length && (n = in.read(buffer, total, buffer.length - total)) != -1) {
            total += n;
        }
        return total;
    }

    private String resolveFileName(String fileName, MultipartFile multipartFile) {
        if (Objects.nonNull(fileName) && !fileName.trim().isEmpty()) {
            return fileName.trim();
        }
        String original = multipartFile.getOriginalFilename();
        return Objects.nonNull(original) ? original : "unnamed.csv";
    }

}

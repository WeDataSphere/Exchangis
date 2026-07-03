package com.webank.wedatasphere.exchangis.job.server.configuration;

import org.apache.linkis.common.conf.CommonVars;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.MultipartConfigElement;

/**
 * File source configuration (文件 source 配置)
 *
 * <p>Custom business params are read via Linkis {@link CommonVars} from
 * {@code dss-exchangis-server.properties}; the yaml is kept minimal.
 *
 * <p>自定义业务参数通过 Linkis {@link CommonVars} 从 {@code dss-exchangis-server.properties}
 * 读取，yaml 不维护自定义参数。
 */
@Configuration
public class FileSourceConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(FileSourceConfiguration.class);

    /**
     * Max upload file size in bytes (上传文件大小上限), default 10GB
     */
    public static final CommonVars<Long> MAX_FILE_SIZE =
            CommonVars.apply("wds.exchangis.file-source.max-file-size", 10737418240L);

    /**
     * Bounded in-memory parse buffer for streaming parse (流式解析内存缓冲上限), default 8MB
     *
     * <p>M3'': only the first N bytes (enough for header + sample rows) are buffered in memory;
     * the rest of the upstream is streamed directly to BML without full buffering.
     */
    public static final CommonVars<Integer> PARSE_BUFFER_BYTES =
            CommonVars.apply("wds.exchangis.file-source.parse-buffer-bytes", 8 * 1024 * 1024);

    /**
     * Sample rows for type inference (M4) (类型推断采样行数), default 100
     */
    public static final CommonVars<Integer> SAMPLE_ROWS =
            CommonVars.apply("wds.exchangis.file-source.sample-rows", 100);

    /**
     * Header rows read as character stream for encoding/separator/header detection
     * (头信息阶段读取行数), default 2
     */
    public static final CommonVars<Integer> HEADER_ROWS =
            CommonVars.apply("wds.exchangis.file-source.header-rows", 2);

    /**
     * Multipart config for large file uploads (大文件上传 multipart 配置)
     *
     * <p>Registered programmatically so that {@code application.yml} stays minimal and the limit
     * follows {@link #MAX_FILE_SIZE}. {@code fileSizeThreshold=1MB} keeps small files in memory
     * and lets large files spill to the servlet container's temp dir; Exchangis application code
     * never writes temp files itself — it pipes {@code MultipartFile.getInputStream()} straight
     * to BML.
     *
     * <p>编程式注册，使 {@code application.yml} 保持极简，上限跟随 {@link #MAX_FILE_SIZE}。
     * Exchangis 应用代码本身不落临时文件，直接将 {@code MultipartFile.getInputStream()} 透传给 BML。
     */
    @Bean
    public MultipartConfigElement multipartConfigElement() {
        long maxBytes = MAX_FILE_SIZE.getValue();
        // location=null -> servlet container default temp dir; maxFileSize; maxRequestSize; fileSizeThreshold=1MB
        MultipartConfigElement element = new MultipartConfigElement(
                null, maxBytes, maxBytes, 1024 * 1024);
        LOG.info("File source multipart config initialized (文件 source multipart 配置已初始化): maxFileSize={} bytes", maxBytes);
        return element;
    }
}

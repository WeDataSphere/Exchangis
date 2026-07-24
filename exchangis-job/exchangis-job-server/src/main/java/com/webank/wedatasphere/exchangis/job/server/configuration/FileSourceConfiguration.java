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
     * Extra date formats for file-source DATE type inference, comma-separated
     * (e.g. "yyyy/MM/dd,yyyy.MM.dd"). APPENDED to the 3 base formats
     * ("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd", "HH:mm:ss") mirrored from DataX
     * {@code ColumnCast.StringCast.asDate}. Invalid patterns are skipped at first use
     * with a warning.
     *
     * 文件 source DATE 类型推断的额外日期格式，逗号分隔（如 "yyyy/MM/dd,yyyy.MM.dd"）；
     * 追加到对齐 DataX {@code ColumnCast.StringCast.asDate} 的三种基础格式之后，无效模式在首次使用时跳过并告警。
     */
    public static final CommonVars<String> DATE_INFER_EXTRA_FORMATS =
            CommonVars.apply("wds.exchangis.file-source.date-infer-extra-formats", "");

    /**
     * Max ratio of U+FFFD replacement chars tolerated when trial-decoding an encoding candidate
     * during charset detection (编码检测候选择优时允许的 U+FFFD 替换字符占比上限), default 0.01 (1%).
     *
     * <p>A correct encoding decodes real text with ~0 replacement chars; a wrong multi-byte
     * encoding (e.g. GBK bytes decoded as UTF-8) produces many. Candidates whose replacement
     * rate exceeds this are "dirty" and only used as a last resort. Permissive single-byte
     * charsets (ISO-8859-1/windows-1252) always decode at rate 0, so they are deprioritized
     * separately in {@code StreamFileHeaderParser.detectEncoding}.
     */
    public static final CommonVars<Double> ENCODING_REPLACEMENT_THRESHOLD =
            CommonVars.apply("wds.exchangis.file-source.encoding-replacement-threshold", 0.01);

    /**
     * Comma-separated CJK encoding priority (highest first), used to break ties when several CJK
     * multi-byte charsets all decode a sample cleanly (e.g. a GBK file also decodes cleanly under
     * EUC-KR, since their byte ranges overlap and neither produces U+FFFD). Lower position =
     * higher priority. A Korean deployment can put EUC-KR before GB18030.
     *
     * 逗号分隔的 CJK 编码优先级（从高到低），用于多个 CJK 多字节编码都能干净解码时打破平局
     * （如 GBK 文件在 EUC-KR 下也能干净解码，二者字节范围重叠且都不产生 U+FFFD）。位置越前优先级越高。
     * 韩文部署可将 EUC-KR 置于 GB18030 之前。
     */
    public static final CommonVars<String> CJK_ENCODING_PRIORITY =
            CommonVars.apply("wds.exchangis.file-source.cjk-encoding-priority",
                    "UTF-8,GB18030,GBK,GB2312,Big5,Shift_JIS,windows-31j,EUC-JP,EUC-KR,ISO-2022-JP,ISO-2022-KR");

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

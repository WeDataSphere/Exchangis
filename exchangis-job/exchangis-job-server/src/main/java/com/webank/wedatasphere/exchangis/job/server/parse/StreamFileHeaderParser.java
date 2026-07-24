package com.webank.wedatasphere.exchangis.job.server.parse;

import com.webank.wedatasphere.exchangis.job.server.configuration.FileSourceConfiguration;
import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.apache.commons.lang3.time.FastDateFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParsePosition;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Stream file header parser (流式文件头解析器)
 *
 * <p>Two-stage parse over a bounded in-memory buffer (the first N bytes streamed from upstream):
 * <ol>
 *   <li>Header stage (头信息阶段): BOM sniff + ICU4J charset detection + separator scoring
 *       + column name normalization. Fail fast if encoding unidentifiable or format invalid.</li>
 *   <li>Sampling stage (采样阶段): type inference (long→double→boolean→date→string,
 *       aligned with DataX UnstructuredStorageReaderUtil supported column types
 *       STRING/LONG/DOUBLE/BOOLEAN/DATE) and null-rate over the first {@code sampleRows} rows.</li>
 * </ol>
 *
 * <p>ICU4J is the same charset-detection engine used by Apache Tika; using it directly avoids
 * pulling in the heavy {@code tika-parsers} dependency tree.
 *
 * <p>ICU4J 与 Apache Tika 内部使用相同的编码检测引擎，直接使用可避免引入过重的 tika-parsers 依赖。
 */
public class StreamFileHeaderParser {

    private static final Logger LOG = LoggerFactory.getLogger(StreamFileHeaderParser.class);

    /**
     * Fail-fast error codes (fail fast 错误码)
     */
    public static final String ERR_ENCODING = "ENCODING_UNDETECTABLE";
    public static final String ERR_EMPTY = "EMPTY_FILE";
    public static final String ERR_SEPARATOR = "SEPARATOR_UNDETECTABLE";

    /**
     * Candidate separators (multi-hypothesis scoring) / 候选分隔符（多假设打分）
     */
    private static final char[] SEPARATORS = {',', '\t', ';', '|'};

    /**
     * Type inference chain (narrowest -> widest), aligned with DataX
     * UnstructuredStorageReaderUtil supported column types (STRING/LONG/DOUBLE/BOOLEAN/DATE).
     * 类型推断链（窄→宽），对齐 DataX UnstructuredStorageReaderUtil 支持的列类型。
     */
    private static final String[] TYPE_CHAIN =
            {"LONG", "DOUBLE", "BOOLEAN", "DATE", "STRING"};

    /**
     * Base date formats mirrored from DataX {@code ColumnCast.StringCast.asDate}
     * (datetime / date / time), always used for DATE type inference. Extra formats from
     * {@link FileSourceConfiguration#DATE_INFER_EXTRA_FORMATS} (Linkis CommonVars) are
     * appended at first use. Full-input match required to reject prefix matches like
     * "2024-01-01abc".
     * 基础日期格式，对齐 DataX {@code ColumnCast.StringCast.asDate} 的三种格式，始终参与 DATE 推断；
     * 额外格式通过 {@link FileSourceConfiguration#DATE_INFER_EXTRA_FORMATS}（Linkis CommonVars）在首次使用时追加。
     * 要求整串匹配，拒绝 "2024-01-01abc" 这类前缀匹配。
     */
    private static final FastDateFormat[] BASE_DATE_FORMATS = {
            FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss"),
            FastDateFormat.getInstance("yyyy-MM-dd"),
            FastDateFormat.getInstance("HH:mm:ss")
    };

    /**
     * Cached combined date formats (base + extra). Lazily built on first DATE inference
     * so that Linkis CommonVars / {@link FileSourceConfiguration} is already initialized.
     * 缓存的完整日期格式（基础+额外），在首次 DATE 推断时懒构建，确保 CommonVars/FileSourceConfiguration 已初始化。
     */
    private static volatile FastDateFormat[] cachedDateFormats;

    /**
     * Get the combined date formats (base + config extra), lazily cached.
     * 获取完整日期格式（基础+配置额外），懒缓存。
     */
    private static FastDateFormat[] getDateFormats() {
        FastDateFormat[] cached = cachedDateFormats;
        if (cached != null) {
            return cached;
        }
        synchronized (StreamFileHeaderParser.class) {
            if (cachedDateFormats == null) {
                cachedDateFormats = buildDateFormats(readExtraDateFormats());
            }
            return cachedDateFormats;
        }
    }

    /**
     * Read the extra date formats config; fall back to empty when CommonVars is not
     * available (e.g. unit tests without Linkis config bootstrap).
     * 读取额外日期格式配置；CommonVars 不可用时（如未引导 Linkis 配置的单元测试）回退为空。
     */
    private static String readExtraDateFormats() {
        try {
            return FileSourceConfiguration.DATE_INFER_EXTRA_FORMATS.getValue();
        } catch (Throwable t) {
            LOG.warn("Cannot read extra date formats config, use base formats only "
                    + "(无法读取额外日期格式配置，仅使用基础格式): {}", t.getMessage());
            return "";
        }
    }

    /**
     * Max bytes decoded per encoding candidate for the replacement-rate sanity check. Bounded so
     * trial-decoding many candidates stays cheap; 32KB is plenty to estimate the U+FFFD ratio.
     * 每个编码候选为替换率健全性校验解码的最大字节数。有界以控制多候选尝试成本；32KB 足以估算 U+FFFD 占比。
     */
    private static final int RATE_CHECK_LIMIT = 32 * 1024;

    /**
     * Read the encoding replacement-rate threshold from config; fall back to 1% when CommonVars is
     * unavailable (e.g. unit tests without Linkis config bootstrap).
     * 从配置读取编码替换率阈值；CommonVars 不可用时（如未引导 Linkis 配置的单元测试）回退 1%。
     */
    private static double getEncodingReplacementThreshold() {
        try {
            return FileSourceConfiguration.ENCODING_REPLACEMENT_THRESHOLD.getValue();
        } catch (Throwable t) {
            return 0.01;
        }
    }

    /**
     * Build the combined date formats: base formats + extra patterns parsed from a
     * comma-separated config string. Invalid patterns are skipped with a warning.
     * 构建完整日期格式：基础格式 + 逗号分隔配置字符串解析出的额外模式；无效模式跳过并告警。
     *
     * <p>Package-private for unit testing. (包级可见以便单元测试。)
     *
     * @param extraFormatsCsv comma-separated extra date patterns, may be null/empty
     *                        (逗号分隔的额外日期模式，可为 null/空)
     * @return combined non-null date format array (合并后的非空日期格式数组)
     */
    static FastDateFormat[] buildDateFormats(String extraFormatsCsv) {
        List<FastDateFormat> formats = new ArrayList<>(BASE_DATE_FORMATS.length + 4);
        Collections.addAll(formats, BASE_DATE_FORMATS);
        if (extraFormatsCsv != null && !extraFormatsCsv.trim().isEmpty()) {
            for (String token : extraFormatsCsv.split(",")) {
                String pattern = token.trim();
                if (pattern.isEmpty()) {
                    continue;
                }
                try {
                    formats.add(FastDateFormat.getInstance(pattern));
                } catch (Exception e) {
                    LOG.warn("Skip invalid date format pattern (跳过无效日期格式): [{}], {}",
                            pattern, e.getMessage());
                }
            }
        }
        return formats.toArray(new FastDateFormat[0]);
    }

    /**
     * Null value tokens / 空值识别
     */
    private static final Set<String> NULL_TOKENS = new HashSet<>(Arrays.asList("", "null", "NULL", "Null", "\\N"));

    /**
     * Parse the bounded buffer and return the parse result (fail fast on bad header).
     *
     * @param buffer     bounded byte buffer (first N bytes from upstream)
     * @param length     valid byte length in buffer
     * @param fileName   file name
     * @param fileSize   total file size in bytes
     * @param sampleRows sample rows for type inference (M4)
     * @param headerRows header rows for encoding/separator/header detection
     * @return parse result (with errorCode set if fail fast)
     */
    public FileParseResult parse(byte[] buffer, int length, String fileName,
                                 long fileSize, int sampleRows, int headerRows) {
        FileParseResult result = new FileParseResult();
        result.setFileName(fileName);
        result.setFileSize(fileSize);
        // File format (csv/text) for DataX txtfilereader Key.FILE_FORMAT. Returned to the
        // frontend so the sync-task submission can forward it to txtfilereader; without it
        // txtfilereader always defaults to "csv", which mis-parses plain-text (.txt) files
        // whose fields need the simple delimiter split. Set early so it is present even on
        // fail-fast.
        // 文件格式（csv/text），对应 DataX txtfilereader Key.FILE_FORMAT。随响应返回前端，供提交
        // 同步任务时透传给 txtfilereader；缺失时 txtfilereader 恒默认 csv，会把纯文本(.txt)文件
        // 按 CSV 引号规则误解析。提前设置，fail fast 时也携带。
        result.setFileFormat(detectFileFormat(fileName));

        // 1. Encoding detection (BOM sniff + ICU4J) / 编码检测
        EncodingDetection enc = detectEncoding(buffer, length);
        if (enc == null) {
            result.setErrorCode(ERR_ENCODING);
            result.setErrorMsg("Cannot detect file encoding (无法识别文件编码)");
            return result;
        }
        result.setEncoding(enc.charset.name());
        result.setHasBom(enc.hasBom);
        LOG.info("File source parse encoding detected (文件编码检测): file={}, charset={}, hasBom={}, confidence={}",
                fileName, enc.charset.name(), enc.hasBom, enc.confidence);

        // 2. Decode bytes to text (skip BOM) / 解码为文本（跳过 BOM）
        String text;
        try {
            text = new String(buffer, enc.bomLength, Math.max(0, length - enc.bomLength), enc.charset);
        } catch (Exception e) {
            result.setErrorCode(ERR_ENCODING);
            result.setErrorMsg("Fail to decode file content (文件内容解码失败): " + e.getMessage());
            return result;
        }

        // 3. Split into lines (handle \r\n / \n / \r) / 按行切分
        List<String> lines = splitLines(text);
        // If the buffer was truncated (file larger than the parse buffer), the last line may be
        // cut mid-character/mid-field and contain a stray U+FFFD. Drop it so it doesn't pollute
        // separator scoring / type inference / samples. Only the last line (buffer tail) is affected.
        // 缓冲被截断时（文件大于解析缓冲），末行可能在字符/字段中间被切断并含孤立 U+FFFD，丢弃以免污染
        // 分隔符打分/类型推断/采样。仅末行（缓冲尾部）受影响。
        if (length < fileSize && !lines.isEmpty()
                && lines.get(lines.size() - 1).indexOf('�') >= 0) {
            lines.remove(lines.size() - 1);
        }
        if (lines.isEmpty()) {
            result.setErrorCode(ERR_EMPTY);
            result.setErrorMsg("Empty file (文件为空)");
            return result;
        }

        // 4. Score separators on the first (headerRows + sampleRows) lines / 分隔符打分
        int scoreLines = Math.min(lines.size(), headerRows + sampleRows);
        Character separator = scoreSeparator(lines, scoreLines);
        if (separator == null) {
            result.setErrorCode(ERR_SEPARATOR);
            result.setErrorMsg("Cannot detect a consistent separator (无法识别一致的分隔符)");
            return result;
        }
        result.setSeparator(separator);

        // 5. Header row + raw column names / 首行表头与原始列名
        String headerLine = lines.get(0);
        result.setHeaderRow(headerLine);
        List<String> rawColumns = splitLine(headerLine, separator);
        // CSV convention: first row is header / CSV 约定首行为表头
        boolean hasHeader = true;
        result.setHasHeader(hasHeader);

        // 6. Normalize column names + build column defines / 列名规范化 + 构建列定义
        Set<String> usedNames = new HashSet<>();
        List<FileColumnDefine> columns = new ArrayList<>();
        for (int i = 0; i < rawColumns.size(); i++) {
            String rawName = rawColumns.get(i);
            String normName = normalizeName(rawName, i, usedNames);
            usedNames.add(normName);
            columns.add(new FileColumnDefine(normName, rawName, "STRING"));
        }

        // 7. Type inference + null rate over sample rows / 类型推断 + 空值率
        int colCount = columns.size();
        int[] nullCounts = new int[colCount];
        String[] types = new String[colCount];
        Arrays.fill(types, "LONG");
        int startRow = hasHeader ? 1 : 0;
        int sampleCount = Math.min(sampleRows, lines.size() - startRow);
        if (sampleCount < 0) {
            sampleCount = 0;
        }
        for (int r = 0; r < sampleCount; r++) {
            List<String> values = splitLine(lines.get(startRow + r), separator);
            for (int c = 0; c < colCount; c++) {
                String v = c < values.size() ? values.get(c) : null;
                if (isNullValue(v)) {
                    nullCounts[c]++;
                } else {
                    types[c] = inferType(types[c], v);
                    List<String> samples = columns.get(c).getSampleValues();
                    // Keep only the FIRST non-null sample value. For field-mapping preview a
                    // single representative value is enough; collecting more only adds noise.
                    // 仅保留第一个非空采样值。字段映射预览只需一个代表性值，多取反而干扰。
                    if (samples.isEmpty()) {
                        samples.add(v);
                    }
                }
            }
        }
        for (int c = 0; c < colCount; c++) {
            columns.get(c).setInferredType(types[c]);
            columns.get(c).setNullRate(sampleCount > 0 ? (double) nullCounts[c] / sampleCount : 0.0);
        }
        result.setColumns(columns);
        result.setSampledRowCount(sampleCount);
        return result;
    }

    /**
     * Detect the DataX file format (csv/text) from the file-name extension. Maps to DataX
     * {@code Key.FILE_FORMAT} (txtfilereader): "csv" uses the OpenCSV CsvReader (handles
     * quoted fields per RFC 4180), "text" uses a plain delimiter split. Defaults to "csv"
     * (matches DataX's own default and the design-doc DDL default CSV) for unknown/null
     * extensions. This is the single source of truth for file format; the service derives
     * the DB {@code file_type} column from it (uppercased).
     *
     * 从文件名扩展名推断 DataX 文件格式（csv/text），对应 DataX {@code Key.FILE_FORMAT}
     * （txtfilereader）：csv 用 OpenCSV CsvReader（按 RFC 4180 处理引号字段），text 用简单分隔符切分。
     * 扩展名未知/null 时默认 csv（与 DataX 自身默认及设计文档 DDL 默认 CSV 一致）。本方法为文件格式
     * 的唯一来源，服务层据此（大写）派生 DB {@code file_type} 列。
     */
    private String detectFileFormat(String fileName) {
        if (fileName == null) {
            return "csv";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".txt") || lower.endsWith(".text")) {
            return "text";
        }
        return "csv";
    }

    /**
     * Detect encoding: BOM sniff first, then ICU4J (BOM→ICU4J→UTF-8 fallback).
     * 编码检测：先 BOM 嗅探，再 ICU4J（BOM→ICU4J→UTF-8 兜底）。
     */
    private EncodingDetection detectEncoding(byte[] buffer, int length) {
        if (length == 0) {
            return null;
        }
        // BOM sniff (BOM 嗅探)
        if (length >= 3 && (buffer[0] & 0xFF) == 0xEF && (buffer[1] & 0xFF) == 0xBB && (buffer[2] & 0xFF) == 0xBF) {
            EncodingDetection enc = new EncodingDetection();
            enc.charset = StandardCharsets.UTF_8;
            enc.hasBom = true;
            enc.bomLength = 3;
            enc.confidence = 100;
            return enc;
        }
        if (length >= 2 && (buffer[0] & 0xFF) == 0xFF && (buffer[1] & 0xFF) == 0xFE) {
            // FF FE is shared by UTF-16LE and UTF-32LE (FF FE 00 00). Distinguish by bytes 2-3:
            // UTF-32LE has zeros at [2],[3]. The JDK has no native UTF-32 charset, so for UTF-32LE
            // we fall through to ICU4J instead of silently misdecoding as UTF-16LE.
            // FF FE 同时是 UTF-16LE 与 UTF-32LE(FF FE 00 00) 的 BOM，用第 2-3 字节区分：UTF-32LE 在
            // [2],[3] 为 0。JDK 无原生 UTF-32 字符集，UTF-32LE 交给 ICU4J，避免静默按 UTF-16LE 误解码。
            if (!(length >= 4 && (buffer[2] & 0xFF) == 0x00 && (buffer[3] & 0xFF) == 0x00)) {
                EncodingDetection enc = new EncodingDetection();
                enc.charset = Charset.forName("UTF-16LE");
                enc.hasBom = true;
                enc.bomLength = 2;
                enc.confidence = 100;
                return enc;
            }
            LOG.warn("UTF-32LE BOM detected; JDK has no native UTF-32, falling back to ICU4J (检测到 UTF-32LE BOM，JDK 无原生 UTF-32，回退 ICU4J)");
        }
        if (length >= 2 && (buffer[0] & 0xFF) == 0xFE && (buffer[1] & 0xFF) == 0xFF) {
            EncodingDetection enc = new EncodingDetection();
            enc.charset = Charset.forName("UTF-16BE");
            enc.hasBom = true;
            enc.bomLength = 2;
            enc.confidence = 100;
            return enc;
        }
        // ICU4J detection with candidate clean-decode trial (ICU4J 检测 + 候选择优)
        // Fixes the bug where a low-confidence ICU4J match (e.g. ISO-8859-1 for a GBK file) was
        // trusted blindly: permissive single-byte charsets decode almost any byte without U+FFFD,
        // so "no replacement char" alone does not prove correctness. We (1) take ICU4J's ranked
        // detectAll() candidates, (2) trial-decode a bounded prefix of each and measure the U+FFFD
        // ratio, (3) pick the first CLEAN MULTI-BYTE candidate (deprioritizing permissive
        // single-byte ones which are frequent CJK false positives). If no clean multi-byte
        // candidate exists, fall back to the lowest-replacement-rate candidate (this correctly
        // keeps a true Latin-1 file on ISO-8859-1).
        // 修复「盲信 ICU4J 低置信度结果（如 GBK 被误判 ISO-8859-1）」缺陷：宽松单字节编码几乎不产生
        // U+FFFD，「无替换字符」不能证明正确。故取 ICU4J 排序候选 detectAll()，逐个解码前缀测 U+FFFD 占比，
        // 选首个「干净」多字节候选（降低宽松单字节候选优先级，因其常是 CJK 误检）；若无干净多字节候选，
        // 回退到替换率最低者（这样真正的 Latin-1 文件仍会选 ISO-8859-1）。
        try {
            byte[] sample = length == buffer.length ? buffer : Arrays.copyOf(buffer, length);
            CharsetDetector detector = new CharsetDetector();
            detector.setText(sample);
            CharsetMatch[] matches = detector.detectAll();
            if (matches == null || matches.length == 0) {
                return null;
            }
            double threshold = getEncodingReplacementThreshold();
            // Pass 1: first (highest-confidence) clean multi-byte candidate wins.
            // Pass 1：首个（置信度最高）「干净」多字节候选胜出。
            Charset chosen = null;
            int chosenConfidence = 0;
            for (CharsetMatch m : matches) {
                Charset cs;
                try {
                    cs = Charset.forName(m.getName());
                } catch (Exception e) {
                    continue;
                }
                if (!isPermissiveSingleByte(cs) && replacementRate(sample, cs) <= threshold) {
                    chosen = cs;
                    chosenConfidence = m.getConfidence();
                    break;
                }
            }
            // Pass 2: no clean multi-byte candidate -> pick the lowest-replacement-rate candidate
            // overall (a true Latin-1 file stays on ISO-8859-1; a file ICU4J failed to identify
            // gets the least-garbled option).
            // Pass 2：无干净多字节候选 -> 取替换率最低者（真 Latin-1 文件仍选 ISO-8859-1；ICU4J 未识别者取最少乱码项）。
            if (chosen == null) {
                Charset best = null;
                double bestRate = Double.MAX_VALUE;
                int bestConfidence = 0;
                for (CharsetMatch m : matches) {
                    Charset cs;
                    try {
                        cs = Charset.forName(m.getName());
                    } catch (Exception e) {
                        continue;
                    }
                    double rate = replacementRate(sample, cs);
                    if (rate < bestRate) {
                        bestRate = rate;
                        best = cs;
                        bestConfidence = m.getConfidence();
                    }
                }
                if (best != null) {
                    chosen = best;
                    chosenConfidence = bestConfidence;
                    LOG.warn("No clean multi-byte encoding candidate; using lowest-replacement-rate "
                            + "(无干净多字节编码候选，使用替换率最低者): {}, rate={}", chosen.name(), bestRate);
                }
            }
            if (chosen == null) {
                return null;
            }
            EncodingDetection enc = new EncodingDetection();
            enc.charset = chosen;
            enc.hasBom = false;
            enc.bomLength = 0;
            enc.confidence = chosenConfidence;
            return enc;
        } catch (Exception e) {
            LOG.warn("ICU4J charset detection failed (ICU4J 编码检测失败): {}", e.getMessage());
            return null;
        }
    }

    /**
     * Decode a bounded prefix of the sample with the given charset and return the ratio of
     * U+FFFD replacement chars. Strict multi-byte charsets (UTF-8/GBK) emit U+FFFD for invalid
     * byte sequences, so a low rate means the charset plausibly fits the content. Package-private
     * for unit testing.
     * 用给定编码解码采样前缀，返回 U+FFFD 替换字符占比。严格多字节编码（UTF-8/GBK）对非法字节序列产生
     * U+FFFD，占比低说明该编码与内容匹配。包级可见以便单元测试。
     */
    double replacementRate(byte[] sample, Charset cs) {
        int limit = Math.min(sample.length, RATE_CHECK_LIMIT);
        String decoded = new String(sample, 0, limit, cs);
        if (decoded.isEmpty()) {
            return 0.0;
        }
        int replacements = 0;
        for (int i = 0; i < decoded.length(); i++) {
            if (decoded.charAt(i) == '�') {
                replacements++;
            }
        }
        return (double) replacements / decoded.length();
    }

    /**
     * Whether the charset is a "permissive" single-byte encoding (ISO-8859-*, windows-125*,
     * US-ASCII, ...). These map almost every byte to a char and never emit U+FFFD, so a clean
     * decode does NOT prove correctness - they are frequent false positives for CJK content
     * (e.g. a GBK file misdetected as ISO-8859-1). Used to deprioritize such candidates.
     * Package-private for unit testing.
     * 是否为「宽松」单字节编码（ISO-8859-*, windows-125*, US-ASCII 等）。此类编码几乎把每个字节都映射成
     * 字符、从不产生 U+FFFD，故「干净解码」不能证明正确--常是 CJK 内容的误检（如 GBK 被误判 ISO-8859-1）。
     * 据此降低其优先级。包级可见以便单元测试。
     */
    boolean isPermissiveSingleByte(Charset cs) {
        String name = cs.name().toLowerCase(Locale.ROOT);
        return name.startsWith("iso-8859") || name.startsWith("iso8859")
                || name.startsWith("windows-125") || name.startsWith("cp125")
                || name.equals("us-ascii") || name.equals("ascii")
                || name.equals("latin1") || name.equals("latin-1")
                || name.equals("tis-620") || name.startsWith("koi8")
                || name.startsWith("macroman") || name.startsWith("mac-roman");
    }

    /**
     * Score separators: pick the one with the highest consistent field count across lines.
     * 分隔符打分：选取各行列数最一致且列数最多的候选。
     */
    private Character scoreSeparator(List<String> lines, int scoreLines) {
        Character best = null;
        int bestScore = 0;
        for (char sep : SEPARATORS) {
            int firstCount = countFields(lines.get(0), sep);
            if (firstCount < 2) {
                continue;
            }
            boolean consistent = true;
            for (int i = 1; i < scoreLines; i++) {
                if (countFields(lines.get(i), sep) != firstCount) {
                    consistent = false;
                    break;
                }
            }
            if (consistent && firstCount > bestScore) {
                bestScore = firstCount;
                best = sep;
            }
        }
        return best;
    }

    private int countFields(String line, char sep) {
        if (line == null || line.isEmpty()) {
            return 0;
        }
        int count = 1;
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    inQuote = false;
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quote = c;
                } else if (c == sep) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * Split a line by separator, respecting simple double/single quote quoting (RFC 4180 lite).
     * 按分隔符切分，支持简单引号转义。
     */
    private List<String> splitLine(String line, char sep) {
        List<String> fields = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            fields.add("");
            return fields;
        }
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuote) {
                if (c == quote) {
                    // doubled quote -> literal quote / 双引号转义
                    if (i + 1 < line.length() && line.charAt(i + 1) == quote) {
                        cur.append(quote);
                        i++;
                    } else {
                        inQuote = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == '"' || c == '\'') {
                    inQuote = true;
                    quote = c;
                } else if (c == sep) {
                    fields.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(c);
                }
            }
        }
        fields.add(cur.toString());
        return fields;
    }

    /**
     * Normalize column name: trim; illegal chars to underscore; leading digit to "_";
     * dedup by appending _1/_2; keep Chinese.
     * 列名规范化：去空格；非法字符转下划线；首字符为数字加下划线前缀；重名加序号；保留中文。
     */
    private String normalizeName(String raw, int index, Set<String> usedNames) {
        if (raw == null) {
            raw = "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            trimmed = "col_" + index;
        }
        StringBuilder sb = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_' || (c >= 0x4E00 && c <= 0x9FFF)) {
                sb.append(c);
            } else {
                sb.append('_');
            }
        }
        String name = sb.toString();
        if (!name.isEmpty() && Character.isDigit(name.charAt(0))) {
            name = "_" + name;
        }
        // Dedup (重名加序号)
        if (usedNames.contains(name)) {
            int suffix = 1;
            String candidate;
            do {
                candidate = name + "_" + suffix;
                suffix++;
            } while (usedNames.contains(candidate));
            name = candidate;
        }
        return name;
    }

    private boolean isNullValue(String v) {
        return v == null || NULL_TOKENS.contains(v);
    }

    /**
     * Infer the column type after seeing a new value: advance along the chain until a type
     * can parse the value (or fall back to STRING).
     * 类型推断：沿链前进直到找到能解析当前值的类型（否则降级 STRING）。
     */
    private String inferType(String current, String value) {
        int startIdx = 0;
        for (int i = 0; i < TYPE_CHAIN.length; i++) {
            if (TYPE_CHAIN[i].equals(current)) {
                startIdx = i;
                break;
            }
        }
        for (int i = startIdx; i < TYPE_CHAIN.length; i++) {
            if (canParse(TYPE_CHAIN[i], value)) {
                return TYPE_CHAIN[i];
            }
        }
        return "STRING";
    }

    /**
     * Check whether a candidate type can parse the given value.
     * Type names align with DataX {@code Type} enum (STRING/LONG/DOUBLE/BOOLEAN/DATE).
     * 校验候选类型能否解析给定值；类型名对齐 DataX {@code Type} 枚举。
     */
    private boolean canParse(String type, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            switch (type) {
                case "LONG":
                    Long.parseLong(value);
                    return true;
                case "DOUBLE":
                    Double.parseDouble(value);
                    return true;
                case "BOOLEAN":
                    return value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false");
                case "DATE":
                    return isDateFormat(value);
                case "STRING":
                    return true;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Check whether the value matches any of the DataX {@code ColumnCast.StringCast.asDate}
     * date formats (datetime "yyyy-MM-dd HH:mm:ss", date "yyyy-MM-dd", time "HH:mm:ss").
     * Full-input match required to reject prefix matches like "2024-01-01abc".
     * 校验值是否匹配 DataX {@code ColumnCast.StringCast.asDate} 的任一日期格式；要求整串匹配，
     * 拒绝 "2024-01-01abc" 这类前缀匹配。
     */
    private boolean isDateFormat(String value) {
        for (FastDateFormat format : getDateFormats()) {
            ParsePosition pos = new ParsePosition(0);
            format.parse(value, pos);
            if (pos.getIndex() == value.length()) {
                return true;
            }
        }
        return false;
    }

    private List<String> splitLines(String text) {
        List<String> lines = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                String line = text.substring(start, i);
                if (line.endsWith("\r")) {
                    line = line.substring(0, line.length() - 1);
                }
                lines.add(line);
                start = i + 1;
            } else if (c == '\r' && (i + 1 >= text.length() || text.charAt(i + 1) != '\n')) {
                lines.add(text.substring(start, i));
                start = i + 1;
            }
        }
        if (start < text.length()) {
            lines.add(text.substring(start));
        }
        // Drop trailing empty line produced by final newline / 丢弃末尾换行产生的空行
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }

    /**
     * Encoding detection result (internal)
     */
    private static class EncodingDetection {
        private Charset charset;
        private boolean hasBom;
        private int bomLength;
        private int confidence;
    }
}

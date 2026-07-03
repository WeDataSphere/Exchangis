package com.webank.wedatasphere.exchangis.job.server.parse;

import com.ibm.icu.text.CharsetDetector;
import com.ibm.icu.text.CharsetMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Stream file header parser (流式文件头解析器)
 *
 * <p>Two-stage parse over a bounded in-memory buffer (the first N bytes streamed from upstream):
 * <ol>
 *   <li>Header stage (头信息阶段): BOM sniff + ICU4J charset detection + separator scoring
 *       + column name normalization. Fail fast if encoding unidentifiable or format invalid.</li>
 *   <li>Sampling stage (采样阶段): type inference (int→long→double→decimal→date→timestamp→string)
 *       and null-rate over the first {@code sampleRows} rows.</li>
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
     * Type inference chain (narrowest -> widest) / 类型推断链（窄→宽）
     */
    private static final String[] TYPE_CHAIN =
            {"int", "long", "double", "decimal", "date", "timestamp", "string"};

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
            columns.add(new FileColumnDefine(normName, rawName, "string"));
        }

        // 7. Type inference + null rate over sample rows / 类型推断 + 空值率
        int colCount = columns.size();
        int[] nullCounts = new int[colCount];
        String[] types = new String[colCount];
        Arrays.fill(types, "int");
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
                    if (samples.size() < 5) {
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
            EncodingDetection enc = new EncodingDetection();
            enc.charset = Charset.forName("UTF-16LE");
            enc.hasBom = true;
            enc.bomLength = 2;
            enc.confidence = 100;
            return enc;
        }
        if (length >= 2 && (buffer[0] & 0xFF) == 0xFE && (buffer[1] & 0xFF) == 0xFF) {
            EncodingDetection enc = new EncodingDetection();
            enc.charset = Charset.forName("UTF-16BE");
            enc.hasBom = true;
            enc.bomLength = 2;
            enc.confidence = 100;
            return enc;
        }
        // ICU4J detection (ICU4J 检测)
        try {
            byte[] sample = length == buffer.length ? buffer : Arrays.copyOf(buffer, length);
            CharsetDetector detector = new CharsetDetector();
            detector.setText(sample);
            CharsetMatch match = detector.detect();
            if (match == null) {
                return null;
            }
            String name = match.getName();
            int confidence = match.getConfidence();
            // Fallback to UTF-8 if confidence too low / 置信度过低兜底 UTF-8
            Charset charset;
            try {
                charset = Charset.forName(name);
            } catch (Exception e) {
                charset = StandardCharsets.UTF_8;
            }
            EncodingDetection enc = new EncodingDetection();
            enc.charset = charset;
            enc.hasBom = false;
            enc.bomLength = 0;
            enc.confidence = confidence;
            return enc;
        } catch (Exception e) {
            LOG.warn("ICU4J charset detection failed (ICU4J 编码检测失败): {}", e.getMessage());
            return null;
        }
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
     * can parse the value (or fall back to string).
     * 类型推断：沿链前进直到找到能解析当前值的类型（否则降级 string）。
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
        return "string";
    }

    private boolean canParse(String type, String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        try {
            switch (type) {
                case "int":
                    Integer.parseInt(value);
                    return true;
                case "long":
                    Long.parseLong(value);
                    return true;
                case "double":
                    Double.parseDouble(value);
                    return true;
                case "decimal":
                    new java.math.BigDecimal(value);
                    return true;
                case "date":
                    return value.matches("\\d{4}-\\d{2}-\\d{2}");
                case "timestamp":
                    return value.matches("\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}.*");
                case "string":
                    return true;
                default:
                    return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
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

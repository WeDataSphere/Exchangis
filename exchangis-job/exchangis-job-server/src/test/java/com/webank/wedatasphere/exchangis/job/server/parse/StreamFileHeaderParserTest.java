package com.webank.wedatasphere.exchangis.job.server.parse;

import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StreamFileHeaderParser} type inference
 * ({@link StreamFileHeaderParser} 类型推断单元测试).
 *
 * <p>Verifies the inferred column types align with DataX
 * {@code UnstructuredStorageReaderUtil} supported types: STRING/LONG/DOUBLE/BOOLEAN/DATE.
 * (验证推断的列类型与 DataX UnstructuredStorageReaderUtil 支持的类型一致：STRING/LONG/DOUBLE/BOOLEAN/DATE。)
 */
class StreamFileHeaderParserTest {

    private final StreamFileHeaderParser parser = new StreamFileHeaderParser();

    /**
     * Parse a CSV string and return a map of column name -> inferred type
     * (解析 CSV 字符串，返回 列名→推断类型 的映射)
     */
    private Map<String, String> parseTypes(String csv) {
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        FileParseResult result = parser.parse(bytes, bytes.length, "test.csv", bytes.length, 10, 1);
        assertNull(result.getErrorCode(),
                "Parse should succeed (解析应成功): " + result.getErrorMsg());
        Map<String, String> types = new HashMap<>();
        for (FileColumnDefine col : result.getColumns()) {
            types.put(col.getName(), col.getInferredType());
        }
        return types;
    }

    @Test
    @DisplayName("Infer LONG/DOUBLE/BOOLEAN/DATE/STRING for a mixed-type CSV "
            + "(混合类型 CSV 推断 LONG/DOUBLE/BOOLEAN/DATE/STRING)")
    void testInferAllSupportedTypes() {
        String csv = String.join("\n",
                "name,age,score,active,birthdate,datetime,note",
                "Alice,30,90.5,true,2024-01-15,2024-01-15 10:30:00,hello",
                "Bob,25,85.0,false,2024-02-20,2024-02-20 14:45:30,world");
        Map<String, String> types = parseTypes(csv);

        assertEquals("STRING", types.get("name"), "name column should be STRING (name 列应为 STRING)");
        assertEquals("LONG", types.get("age"), "age column should be LONG (age 列应为 LONG)");
        assertEquals("DOUBLE", types.get("score"), "score column should be DOUBLE (score 列应为 DOUBLE)");
        assertEquals("BOOLEAN", types.get("active"), "active column should be BOOLEAN (active 列应为 BOOLEAN)");
        assertEquals("DATE", types.get("birthdate"), "birthdate column should be DATE (birthdate 列应为 DATE)");
        assertEquals("DATE", types.get("datetime"), "datetime column should be DATE (datetime 列应为 DATE)");
        assertEquals("STRING", types.get("note"), "note column should be STRING (note 列应为 STRING)");
    }

    @Test
    @DisplayName("LONG stays LONG for pure integers within long range (纯整数列保持 LONG)")
    void testPureLongColumn() {
        String csv = String.join("\n",
                "id,extra",
                "1,x",
                "2,y",
                "9999999999,z");
        Map<String, String> types = parseTypes(csv);
        assertEquals("LONG", types.get("id"), "Integer column should infer LONG (整数列应推断为 LONG)");
    }

    @Test
    @DisplayName("Integer column widens to DOUBLE when a decimal value appears "
            + "(整数列出现小数值时升级为 DOUBLE)")
    void testLongWidensToDouble() {
        String csv = String.join("\n",
                "amount,extra",
                "100,x",
                "200,y",
                "3.14,z");
        Map<String, String> types = parseTypes(csv);
        assertEquals("DOUBLE", types.get("amount"),
                "Column with a decimal value should infer DOUBLE (含小数值的列应推断为 DOUBLE)");
    }

    @Test
    @DisplayName("BOOLEAN for true/false values (true/false 列推断为 BOOLEAN)")
    void testBooleanColumn() {
        String csv = String.join("\n",
                "flag,extra",
                "true,x",
                "false,y",
                "TRUE,z",
                "False,w");
        Map<String, String> types = parseTypes(csv);
        assertEquals("BOOLEAN", types.get("flag"),
                "true/false column should infer BOOLEAN (true/false 列应推断为 BOOLEAN)");
    }

    @Test
    @DisplayName("DATE matches yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, and HH:mm:ss "
            + "(DATE 匹配三种 DataX StringCast.asDate 格式)")
    void testDateFormats() {
        String csv = String.join("\n",
                "d1,d2,d3",
                "2024-01-15,2024-01-15 10:30:00,10:30:00",
                "2024-02-20,2024-02-20 14:45:30,14:45:30");
        Map<String, String> types = parseTypes(csv);
        assertEquals("DATE", types.get("d1"), "yyyy-MM-dd should infer DATE (yyyy-MM-dd 应推断为 DATE)");
        assertEquals("DATE", types.get("d2"), "yyyy-MM-dd HH:mm:ss should infer DATE (datetime 应推断为 DATE)");
        assertEquals("DATE", types.get("d3"), "HH:mm:ss should infer DATE (HH:mm:ss 应推断为 DATE)");
    }

    @Test
    @DisplayName("Values not matching any string2Date format fall to STRING "
            + "(不匹配 string2Date 任一格式的值降级 STRING)")
    void testNonDateFallsToString() {
        // "2024/01/15" uses slashes (string2Date expects dashes); "hello" is not a date.
        // Both fall back to STRING. Note: string2Date (FastDateFormat) is lenient on field
        // ranges (e.g. month 13 rolls over), so such values ARE dates per string2Date;
        // only structurally non-matching values fall to STRING.
        // "2024/01/15" 用斜杠（string2Date 期望连字符）；"hello" 非日期，二者降级 STRING。
        // 注：string2Date(FastDateFormat) 对字段范围宽松（如 13 月会滚动进位），故此类值仍算 DATE；
        // 只有结构上不匹配的值才降级 STRING。
        String csv = String.join("\n",
                "bad,extra",
                "2024/01/15,x",
                "hello,y");
        Map<String, String> types = parseTypes(csv);
        assertEquals("STRING", types.get("bad"),
                "Non-date-format values should fall back to STRING "
                        + "(不匹配日期格式的值应降级 STRING)");
    }

    @Test
    @DisplayName("Date with trailing garbage is NOT DATE — full-match check rejects prefix "
            + "(带尾随垃圾的日期不应判为 DATE，整串匹配拒绝前缀)")
    void testDateWithTrailingGarbageRejects() {
        String csv = String.join("\n",
                "weird,extra",
                "2024-01-01abc,x",
                "2024-01-01xyz,y");
        Map<String, String> types = parseTypes(csv);
        assertEquals("STRING", types.get("weird"),
                "'2024-01-01abc' should NOT be DATE (prefix match must be rejected) "
                        + "(2024-01-01abc 不应判为 DATE，前缀匹配应被拒绝)");
    }

    @Test
    @DisplayName("Inferred types are the exact 5 DataX Type enum names "
            + "(推断类型严格属于 DataX Type 枚举的 5 个名称)")
    void testTypesAreDataXEnumNames() {
        String csv = String.join("\n",
                "name,age,score,active,birthdate",
                "Alice,30,90.5,true,2024-01-15");
        Map<String, String> types = parseTypes(csv);
        List<String> allowed = java.util.Arrays.asList("STRING", "LONG", "DOUBLE", "BOOLEAN", "DATE");
        for (String t : types.values()) {
            assertTrue(allowed.contains(t),
                    "Inferred type must be one of DataX supported types STRING/LONG/DOUBLE/BOOLEAN/DATE, got: "
                            + t + " (推断类型必须属于 DataX 支持的 5 种类型之一)");
        }
    }

    @Test
    @DisplayName("Only the first non-null sample value is kept (仅保留第一个非空采样值)")
    void testSampleValuesKeepFirstOnly() {
        // status column: A,A,B,C,A,D,E -> only the first non-null value "A" is kept.
        // status 列：A,A,B,C,A,D,E -> 仅保留第一个非空值 "A"。
        String csv = String.join("\n",
                "status,extra",
                "A,x",
                "A,y",
                "B,z",
                "C,w",
                "A,q",
                "D,r",
                "E,t");
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        FileParseResult result = parser.parse(bytes, bytes.length, "test.csv", bytes.length, 100, 1);
        assertNull(result.getErrorCode(), "Parse should succeed (解析应成功)");

        List<FileColumnDefine> columns = result.getColumns();
        List<String> statusSamples = columns.get(0).getSampleValues();
        assertEquals(java.util.Arrays.asList("A"), statusSamples,
                "status samples should keep only the first value [A] (status 采样应仅保留第一个值 [A])");
    }

    @Test
    @DisplayName("Sample is the first non-null value, skipping leading nulls "
            + "(采样值为第一个非空值，跳过前导空值)")
    void testSampleValuesSkipsLeadingNulls() {
        // The first data row's value is null (\\N), so the sample should be the first
        // non-null value "B", not null.
        // 第一行数据值为空(\\N)，故采样值应为第一个非空值 "B"。
        String csv = String.join("\n",
                "v,extra",
                "\\N,x",
                "B,y",
                "C,z");
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        FileParseResult result = parser.parse(bytes, bytes.length, "test.csv", bytes.length, 100, 1);
        assertNull(result.getErrorCode(), "Parse should succeed (解析应成功)");

        List<String> samples = result.getColumns().get(0).getSampleValues();
        assertEquals(java.util.Arrays.asList("B"), samples,
                "Sample should be the first non-null value [B] (采样值应为第一个非空值 [B])");
    }

    @Test
    @DisplayName("buildDateFormats: null/empty extra yields only the 3 base formats "
            + "(null/空 额外格式时仅返回 3 种基础格式)")
    void testBuildDateFormatsBaseOnly() {
        assertEquals(3, StreamFileHeaderParser.buildDateFormats(null).length,
                "null extra should yield base 3 (null 额外格式应返回基础 3 种)");
        assertEquals(3, StreamFileHeaderParser.buildDateFormats("").length,
                "empty extra should yield base 3 (空 额外格式应返回基础 3 种)");
        assertEquals(3, StreamFileHeaderParser.buildDateFormats("  ").length,
                "blank extra should yield base 3 (空白 额外格式应返回基础 3 种)");
    }

    @Test
    @DisplayName("buildDateFormats: extra patterns are appended to the base 3 "
            + "(额外格式追加到基础 3 种之后)")
    void testBuildDateFormatsAppendsExtra() {
        FastDateFormat[] formats = StreamFileHeaderParser.buildDateFormats("yyyy/MM/dd");
        assertEquals(4, formats.length,
                "base 3 + 1 extra = 4 (基础 3 + 1 额外 = 4)");
        assertTrue(matchesAnyFormat(formats, "2024/01/15"),
                "Extra pattern yyyy/MM/dd should match 2024/01/15 (额外格式 yyyy/MM/dd 应匹配 2024/01/15)");
        assertTrue(matchesAnyFormat(formats, "2024-01-15"),
                "Base pattern yyyy-MM-dd should still match (基础格式 yyyy-MM-dd 仍应匹配)");
    }

    @Test
    @DisplayName("buildDateFormats: multiple extra patterns, comma-separated "
            + "(多个额外格式，逗号分隔)")
    void testBuildDateFormatsMultipleExtra() {
        FastDateFormat[] formats = StreamFileHeaderParser.buildDateFormats("yyyy/MM/dd,yyyy.MM.dd");
        assertEquals(5, formats.length,
                "base 3 + 2 extra = 5 (基础 3 + 2 额外 = 5)");
        assertTrue(matchesAnyFormat(formats, "2024/01/15"),
                "yyyy/MM/dd should match (yyyy/MM/dd 应匹配)");
        assertTrue(matchesAnyFormat(formats, "2024.01.15"),
                "yyyy.MM.dd should match (yyyy.MM.dd 应匹配)");
    }

    /**
     * Check whether the value fully matches any of the given formats (full-input match).
     * (校验值是否完整匹配给定格式中的任一——整串匹配。)
     */
    private boolean matchesAnyFormat(FastDateFormat[] formats, String value) {
        for (FastDateFormat format : formats) {
            ParsePosition pos = new ParsePosition(0);
            format.parse(value, pos);
            if (pos.getIndex() == value.length()) {
                return true;
            }
        }
        return false;
    }

    @Test
    @DisplayName("fileFormat is csv for .csv/unknown and text for .txt/.text "
            + "(.csv/未知扩展名为 csv，.txt/.text 为 text)")
    void testFileFormatDetection() {
        assertEquals("csv", parseFileFormat("data.csv"),
                ".csv -> csv (.csv -> csv)");
        assertEquals("text", parseFileFormat("data.txt"),
                ".txt -> text (.txt -> text)");
        assertEquals("text", parseFileFormat("data.text"),
                ".text -> text (.text -> text)");
        assertEquals("csv", parseFileFormat("data"),
                "no extension -> csv (无扩展名 -> csv)");
        assertEquals("csv", parseFileFormat(null),
                "null name -> csv (文件名为 null -> csv)");
        assertEquals("text", parseFileFormat("DATA.TXT"),
                "case-insensitive .TXT -> text (大小写不敏感 .TXT -> text)");
    }

    /**
     * Parse a minimal CSV under different file names and return the detected fileFormat.
     * (以最小 CSV 内容配合不同文件名解析，返回检测到的 fileFormat。)
     */
    private String parseFileFormat(String fileName) {
        String csv = "h1,h2\nv1,v2\n";
        byte[] bytes = csv.getBytes(StandardCharsets.UTF_8);
        FileParseResult result = parser.parse(bytes, bytes.length, fileName, bytes.length, 10, 1);
        assertNull(result.getErrorCode(), "Parse should succeed (解析应成功)");
        return result.getFileFormat();
    }

    @Test
    @DisplayName("GBK file is detected and decoded without mojibake (GBK 文件正确检测解码无乱码)")
    void testGbkEncodingDetected() throws Exception {
        // GBK-encoded Chinese with enough CJK chars for ICU4J to surface a GB candidate. Before
        // the fix ICU4J's top match (often ISO-8859-1) was trusted blindly -> mojibake.
        // GBK 编码中文，CJK 字符足够让 ICU4J 给出 GB 系候选。修复前盲信 ICU4J 最高候选
        // （常为 ISO-8859-1）-> 乱码。
        String text = "姓名,年龄,城市\n张三,30,北京\n李四,25,上海\n王五,40,广州\n";
        byte[] gbkBytes = text.getBytes("GBK");
        FileParseResult result = parser.parse(gbkBytes, gbkBytes.length, "data.csv", gbkBytes.length, 10, 1);
        assertNull(result.getErrorCode(), "Parse should succeed (解析应成功): " + result.getErrorMsg());
        // encoding should be GBK/GB18030 family, NOT a permissive single-byte misdetection.
        // 编码应为 GBK/GB18030 系列，而非宽松单字节误检。
        String enc = result.getEncoding().toUpperCase(Locale.ROOT);
        assertTrue(enc.contains("GB"), "GBK file encoding should be GB family (GBK 文件编码应为 GB 系), got: " + enc);
        // header decoded correctly (no mojibake / U+FFFD).
        // 表头正确解码（无乱码 / U+FFFD）。
        java.util.List<String> colNames = new java.util.ArrayList<>();
        for (FileColumnDefine col : result.getColumns()) {
            colNames.add(col.getOriginalName());
        }
        assertTrue(colNames.contains("姓名"), "Header 姓名 should decode correctly (表头 姓名 应正确解码), got: " + colNames);
    }

    @Test
    @DisplayName("Truncated buffer drops last line containing U+FFFD (截断缓冲丢弃含 U+FFFD 的末行)")
    void testTruncatedBufferDropsGarbledLastLine() {
        // A UTF-8 BOM forces deterministic UTF-8 detection (bypassing ICU4J), so a trailing
        // incomplete 3-byte UTF-8 sequence (0xE4 0xB8) reliably becomes U+FFFD and exercises the
        // truncation drop. fileSize > buffer length signals truncation; without the drop the
        // garbled tail line (1 field) breaks separator scoring.
        // UTF-8 BOM 强制确定性检出 UTF-8（绕过 ICU4J），末尾不完整 3 字节 UTF-8 序列(0xE4 0xB8)可靠地
        // 变成 U+FFFD 以验证截断丢弃；fileSize > 缓冲长度标记截断，不丢弃则乱码末行(1 字段)破坏分隔符打分。
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = "id,name\n1,abc\n2,def\n".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = new byte[bom.length + body.length + 2];
        System.arraycopy(bom, 0, truncated, 0, bom.length);
        System.arraycopy(body, 0, truncated, bom.length, body.length);
        truncated[truncated.length - 2] = (byte) 0xE4;
        truncated[truncated.length - 1] = (byte) 0xB8;
        FileParseResult result = parser.parse(truncated, truncated.length, "data.csv", truncated.length + 100, 10, 1);
        assertNull(result.getErrorCode(), "Parse should succeed (解析应成功): " + result.getErrorMsg());
        // 2 valid data rows; the garbled partial tail line is dropped.
        // 2 行有效数据，乱码残缺末行被丢弃。
        assertEquals(2, result.getSampledRowCount(), "Garbled truncated tail line should be dropped (截断乱码末行应被丢弃)");
        assertEquals(",", String.valueOf(result.getSeparator()), "Separator should be comma (分隔符应为逗号)");
    }

    @Test
    @DisplayName("isPermissiveSingleByte flags ISO-8859-1/windows-1252 but not UTF-8/GBK (宽松单字节判定)")
    void testIsPermissiveSingleByte() {
        assertTrue(parser.isPermissiveSingleByte(Charset.forName("ISO-8859-1")));
        assertTrue(parser.isPermissiveSingleByte(Charset.forName("windows-1252")));
        assertFalse(parser.isPermissiveSingleByte(StandardCharsets.UTF_8));
        assertFalse(parser.isPermissiveSingleByte(Charset.forName("GBK")));
    }

    @Test
    @DisplayName("replacementRate is 0 for matching charset, >0 for mismatch (替换率：匹配为 0，不匹配 >0)")
    void testReplacementRate() throws Exception {
        byte[] utf8 = "姓名,年龄\n".getBytes(StandardCharsets.UTF_8);
        assertEquals(0.0, parser.replacementRate(utf8, StandardCharsets.UTF_8), 1e-9,
                "UTF-8 bytes as UTF-8 -> 0 replacement (UTF-8 字节按 UTF-8 解码替换率 0)");
        // GBK bytes decoded as UTF-8 -> many invalid sequences -> >0 replacement rate.
        // GBK 字节按 UTF-8 解码 -> 大量非法序列 -> 替换率 >0。
        byte[] gbk = "姓名,年龄\n".getBytes("GBK");
        double rate = parser.replacementRate(gbk, StandardCharsets.UTF_8);
        assertTrue(rate > 0.0, "GBK bytes as UTF-8 should have >0 replacement (GBK 按 UTF-8 解码替换率应 >0), got: " + rate);
    }

    @Test
    @DisplayName("cjkPriority: UTF-8 first, GBK before EUC-KR, aliases match, non-CJK=-1 (CJK 优先级与别名匹配)")
    void testCjkPriority() {
        // lower index = higher priority
        assertEquals(0, parser.cjkPriority(StandardCharsets.UTF_8),
                "UTF-8 should be priority 0 (UTF-8 优先级 0)");
        assertTrue(parser.cjkPriority(Charset.forName("GB18030")) < parser.cjkPriority(Charset.forName("EUC-KR")),
                "GB18030 should outrank EUC-KR (GB18030 优先级高于 EUC-KR)");
        assertTrue(parser.cjkPriority(Charset.forName("GBK")) < parser.cjkPriority(Charset.forName("EUC-KR")),
                "GBK should outrank EUC-KR so GBK files are not misdetected as Korean (GBK 优先级高于 EUC-KR，避免 GBK 误判韩文)");
        // alias matches canonical: ks_c_5601-1987 is an alias of EUC-KR
        assertEquals(parser.cjkPriority(Charset.forName("EUC-KR")),
                parser.cjkPriority(Charset.forName("ks_c_5601-1987")),
                "alias ks_c_5601-1987 should match EUC-KR priority (别名应命中 EUC-KR 优先级)");
        // non-CJK -> -1
        assertEquals(-1, parser.cjkPriority(Charset.forName("ISO-8859-1")),
                "ISO-8859-1 is not in CJK priority list (ISO-8859-1 不在 CJK 优先级表)");
    }
}

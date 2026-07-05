package com.webank.wedatasphere.exchangis.job.server.parse;

import org.apache.commons.lang3.time.FastDateFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}

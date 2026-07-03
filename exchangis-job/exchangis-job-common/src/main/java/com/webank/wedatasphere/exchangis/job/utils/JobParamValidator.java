package com.webank.wedatasphere.exchangis.job.utils;

import com.webank.wedatasphere.exchangis.common.util.json.Json;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Unified validator for job params (variable key/value).
 * 统一的任务变量 key/value 校验工具类。
 *
 * Provides two core methods:
 * 1. validateAndConvertParams: for DSS execute (lenient - invalid key WARN skip)
 *    用于 DSS executeJob，宽松模式：过滤系统字段 + 非法 key WARN 跳过 + Object->String 转换
 * 2. validateJobParams: for updateJob (strict - invalid key throws exception)
 *    用于 updateJob，严格模式：非法 key 抛 IllegalArgumentException
 *
 * Related requirement: REQ-03 自定义任务变量参数（开源版）
 */
public final class JobParamValidator {

    private static final Logger LOG = LoggerFactory.getLogger(JobParamValidator.class);

    /**
     * Variable key regex: letter or underscore start, only letters/digits/underscores
     * 变量 key 正则：字母或下划线开头，仅含字母数字下划线
     */
    public static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_]*$");

    /**
     * Max key length
     * key 最大长度
     */
    public static final int MAX_KEY_LENGTH = 64;

    /**
     * Max variable count per job
     * 单 job 变量数量上限
     */
    public static final int MAX_PARAM_COUNT = 50;

    /**
     * System param keys whitelist: DSS AppConn protocol fields, NOT treated as business variables.
     * 系统字段白名单：DSS AppConn 协议字段，不作为业务变量处理。
     * Open-source whitelist contains only standard DSS protocol fields.
     */
    public static final Set<String> SYSTEM_PARAM_KEYS;
    static {
        Set<String> sys = new HashSet<>();
        sys.add("execUser");
        sys.add("nodeName");
        sys.add("workspaceName");
        sys.add("runDate");
        SYSTEM_PARAM_KEYS = Collections.unmodifiableSet(sys);
    }

    private JobParamValidator() {}

    /**
     * Validate and convert DSS params to Map<String,String>.
     * 校验并转换 DSS params 为 Map<String,String>。
     *
     * Lenient mode (宽松模式, for DSS executeJob):
     * - filter system fields (过滤系统字段)
     * - skip null value with WARN (null 值 WARN 跳过)
     * - skip invalid key with WARN (非法 key WARN 跳过，不阻断执行)
     * - Object -> String conversion via String.valueOf (Object 转 String)
     *
     * @param params raw DSS params map (Map<String,Object>)
     * @return cleaned and converted Map<String,String> (never null, may be empty)
     */
    public static Map<String, String> validateAndConvertParams(Map<String, Object> params) {
        Map<String, String> result = new HashMap<>();
        if (params == null || params.isEmpty()) {
            return result;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            // Filter system param keys (过滤系统字段)
            if (SYSTEM_PARAM_KEYS.contains(key)) {
                LOG.debug("Skip system param key: {}", key);
                continue;
            }
            // Skip null value (null 值跳过)
            if (value == null) {
                LOG.warn("Skip null value for param key: {}", key);
                continue;
            }
            // Validate key (校验 key)
            if (!isValidKey(key)) {
                LOG.warn("Skip invalid param key: [{}], expected: ^[a-zA-Z_][a-zA-Z0-9_]*$, length 1-{}",
                        key, MAX_KEY_LENGTH);
                continue;
            }
            // Object -> String conversion (Object 转 String)
            result.put(key, String.valueOf(value));
        }
        return result;
    }

    /**
     * Validate jobParams JSON string strictly.
     * 严格校验 jobParams JSON 字符串。
     *
     * Strict mode (严格模式, for updateJob):
     * - parse JSON (解析 JSON)
     * - validate count limit (校验数量上限)
     * - validate key regex (校验 key 正则)
     * - validate key uniqueness (校验 key 唯一性)
     *
     * @param jobParamsJson jobParams JSON string, e.g. {"run_date":"2026-07-01"}
     * @throws IllegalArgumentException if invalid (count exceed / illegal key / duplicate key / invalid JSON)
     */
    public static void validateJobParams(String jobParamsJson) throws IllegalArgumentException {
        if (StringUtils.isBlank(jobParamsJson)) {
            return;
        }
        Map<String, String> params;
        try {
            params = Json.fromJson(jobParamsJson, Map.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid jobParams JSON: " + jobParamsJson);
        }
        if (params == null || params.isEmpty()) {
            return;
        }
        // Validate count limit (校验数量上限)
        if (params.size() > MAX_PARAM_COUNT) {
            throw new IllegalArgumentException(
                    "Variable count exceeds limit " + MAX_PARAM_COUNT);
        }
        // Validate key regex + uniqueness (校验 key 正则 + 唯一性)
        Set<String> seen = new HashSet<>();
        for (String key : params.keySet()) {
            if (!isValidKey(key)) {
                throw new IllegalArgumentException(
                        "Invalid variable name (must match ^[a-zA-Z_][a-zA-Z0-9_]*$): " + key);
            }
            if (!seen.add(key)) {
                throw new IllegalArgumentException("Duplicate variable name: " + key);
            }
        }
    }

    /**
     * Check if key is valid (length + regex).
     * 校验 key 是否合法（长度 + 正则）。
     *
     * @param key variable key
     * @return true if valid
     */
    private static boolean isValidKey(String key) {
        if (StringUtils.isBlank(key) || key.length() > MAX_KEY_LENGTH) {
            return false;
        }
        return KEY_PATTERN.matcher(key).matches();
    }
}

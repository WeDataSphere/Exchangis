package com.alibaba.datax.plugin.reader.shenzhoureader;

import com.alibaba.datax.common.spi.ErrorCode;

/**
 * 神州数据库读插件错误码 / Shenzhou reader plugin error codes
 * <p>
 * 当前暂无自定义错误码，所有异常由通用 RDBMS 框架 DBUtilErrorCode 统一处理。
 * 后续如需添加插件特定错误码（如 HINT 语法错误等），可在此枚举中扩展。
 * Currently no custom error codes; all exceptions handled by the common RDBMS framework DBUtilErrorCode.
 * Plugin-specific error codes (e.g. HINT syntax errors) can be added here in the future.
 * </p>
 */
public enum ShenzhouReaderErrorCode implements ErrorCode {
    ;

    private final String code;
    private final String description;

    private ShenzhouReaderErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    @Override
    public String getCode() {
        return this.code;
    }

    @Override
    public String getDescription() {
        return this.description;
    }

    @Override
    public String toString() {
        return String.format("Code:[%s], Description:[%s]. ", this.code,
                this.description);
    }

}

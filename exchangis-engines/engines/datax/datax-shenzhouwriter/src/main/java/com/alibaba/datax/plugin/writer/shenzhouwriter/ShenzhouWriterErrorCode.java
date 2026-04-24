package com.alibaba.datax.plugin.writer.shenzhouwriter;

import com.alibaba.datax.common.spi.ErrorCode;

/**
 * 神州数据库写插件错误码 / Shenzhou writer plugin error codes
 * <p>
 * 当前暂无自定义错误码，所有异常由通用 RDBMS 框架 DBUtilErrorCode 统一处理。
 * 后续如需添加插件特定错误码（如批量写入冲突等），可在此枚举中扩展。
 * Currently no custom error codes; all exceptions handled by the common RDBMS framework DBUtilErrorCode.
 * Plugin-specific error codes (e.g. batch write conflicts) can be added here in the future.
 * </p>
 */
public enum ShenzhouWriterErrorCode implements ErrorCode {
    ;

    private final String code;
    private final String description;

    private ShenzhouWriterErrorCode(String code, String description) {
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

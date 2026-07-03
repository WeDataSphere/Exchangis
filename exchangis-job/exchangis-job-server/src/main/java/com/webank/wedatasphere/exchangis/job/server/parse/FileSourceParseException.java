package com.webank.wedatasphere.exchangis.job.server.parse;

/**
 * File source parse / upload exception (文件 source 解析/上传异常)
 *
 * <p>Carries an error code (e.g. {@link StreamFileHeaderParser#ERR_ENCODING}) so the controller
 * can translate fail-fast cases into the appropriate HTTP response.
 *
 * <p>携带错误码（如编码不可识别），供 controller 将 fail fast 场景转为合适的 HTTP 响应。
 */
public class FileSourceParseException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public FileSourceParseException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public FileSourceParseException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}

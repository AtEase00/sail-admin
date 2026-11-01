package sail.common.core;

import lombok.Getter;

/** 可直接展示给调用方的业务异常。 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        // 普通业务校验错误统一使用 400。
        this(400, message);
    }

    public BusinessException(int code, String message) {
        // 保留明确业务错误码，避免把系统异常当作业务错误。
        super(message);
        this.code = code;
    }
}

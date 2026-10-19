package jh.com.common;

import lombok.Getter;

/**
 * 自定义刷题基础异常
 */
@Getter
public class BizException extends RuntimeException {
    private final ErrorCode code;

    public BizException(ErrorCode code) {
        super(code.getDefaultMessage());
        this.code = code;
    }

    public BizException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(ErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}

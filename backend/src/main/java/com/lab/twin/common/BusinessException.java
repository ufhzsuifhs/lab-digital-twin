package com.lab.twin.common;

import lombok.Getter;

/**
 * 业务异常：服务层抛出自定义错误，由 GlobalExceptionHandler 统一转成 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}

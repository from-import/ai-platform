package org.frostnova.aigateway.common.exception;

import org.springframework.http.HttpStatus;

public class BaseException extends RuntimeException {

    private final String code;
    private final HttpStatus httpStatus;

    public BaseException(String code, String message) {
        this(code, message, HttpStatus.BAD_REQUEST);
    }

    public BaseException(String code, String message, HttpStatus httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public BaseException(String code, String message, HttpStatus httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }
}

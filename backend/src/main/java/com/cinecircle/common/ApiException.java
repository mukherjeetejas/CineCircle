package com.cinecircle.common;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Business exception translated by {@link GlobalExceptionHandler} into the CLAUDE.md error shape. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final ErrorCode code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, ErrorCode code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public ApiException(ErrorCode code, String message) {
        this(code.defaultStatus(), code, message, null);
    }

    public static ApiException notFound(String message) {
        return new ApiException(ErrorCode.NOT_FOUND, message);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.VALIDATION_ERROR, message, Map.of(field, message));
    }

    public static ApiException notOwner(String message) {
        return new ApiException(ErrorCode.NOT_OWNER, message);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }
}

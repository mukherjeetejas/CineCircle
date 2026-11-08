package com.cinecircle.mcp.error;

import java.util.Map;

/** The backend answered with an error (CLAUDE.md error shape {@code {status, code, message, fieldErrors}}). */
public class CineCircleApiException extends RuntimeException {

    private final int status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public CineCircleApiException(int status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors == null ? Map.of() : Map.copyOf(fieldErrors);
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Map<String, String> fieldErrors() {
        return fieldErrors;
    }
}

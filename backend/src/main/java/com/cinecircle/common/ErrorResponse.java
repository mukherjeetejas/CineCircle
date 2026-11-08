package com.cinecircle.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Error body: { status, code, message, fieldErrors? } as defined in CLAUDE.md. */
public record ErrorResponse(
        int status,
        String code,
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fieldErrors
) {
    public static ErrorResponse of(int status, ErrorCode code, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, code.name(), message, fieldErrors);
    }
}

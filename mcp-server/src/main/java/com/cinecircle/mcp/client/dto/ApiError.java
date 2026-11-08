package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/** Backend error body {@code {status, code, message, fieldErrors?}}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ApiError(int status, String code, String message, Map<String, String> fieldErrors) {
}

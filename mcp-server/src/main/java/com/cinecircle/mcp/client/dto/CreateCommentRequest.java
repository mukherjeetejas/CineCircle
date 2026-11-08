package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** {@code {body, parentCommentId?}} */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CreateCommentRequest(String body, String parentCommentId) {
}

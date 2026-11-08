package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** {@code {id,author,body,deleted,createdAt,isMine,replies[]}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Comment(
        String id,
        UserSummary author,
        String body,
        @JsonProperty("deleted") boolean deleted,
        Instant createdAt,
        @JsonProperty("isMine") boolean isMine,
        List<Comment> replies
) {
}

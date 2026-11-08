package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** {@code {id,username,displayName,avatarUrl,isPrivate}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record UserSummary(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        @JsonProperty("isPrivate") boolean isPrivate
) {
}

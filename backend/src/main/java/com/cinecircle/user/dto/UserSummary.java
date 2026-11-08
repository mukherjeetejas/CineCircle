package com.cinecircle.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** {id,username,displayName,avatarUrl,isPrivate} */
public record UserSummary(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        @JsonProperty("isPrivate") boolean isPrivate
) {
}

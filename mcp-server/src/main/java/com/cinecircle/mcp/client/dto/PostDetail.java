package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** PostSummary with the full {@code body}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostDetail(
        String id,
        UserSummary author,
        PostMovie movie,
        String title,
        String bodyPreview,
        String body,
        int rating,
        @JsonProperty("containsSpoilers") boolean containsSpoilers,
        Spoiler spoiler,
        String imageUrl,
        long upvoteCount,
        long commentCount,
        @JsonProperty("upvotedByMe") boolean upvotedByMe,
        @JsonProperty("isMine") boolean isMine,
        Instant createdAt,
        String source
) {
}

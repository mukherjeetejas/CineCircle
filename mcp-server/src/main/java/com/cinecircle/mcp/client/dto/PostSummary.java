package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * {@code {id,author,movie,title,bodyPreview(<=300),rating,containsSpoilers,spoiler,imageUrl,upvoteCount,commentCount,
 * upvotedByMe,isMine,createdAt,source?}}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostSummary(
        String id,
        UserSummary author,
        PostMovie movie,
        String title,
        String bodyPreview,
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

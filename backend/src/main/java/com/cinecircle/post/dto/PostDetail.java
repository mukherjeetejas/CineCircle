package com.cinecircle.post.dto;

import com.cinecircle.spoiler.SpoilerInfo;
import com.cinecircle.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** PostSummary with the full {@code body}. */
public record PostDetail(
        String id,
        UserSummary author,
        PostMovie movie,
        String title,
        String bodyPreview,
        String body,
        int rating,
        @JsonProperty("containsSpoilers") boolean containsSpoilers,
        SpoilerInfo spoiler,
        String imageUrl,
        long upvoteCount,
        long commentCount,
        @JsonProperty("upvotedByMe") boolean upvotedByMe,
        @JsonProperty("isMine") boolean isMine,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) FeedSource source
) {
}

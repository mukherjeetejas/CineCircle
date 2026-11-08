package com.cinecircle.post.dto;

import com.cinecircle.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * {id,author,movie,title,bodyPreview(<=300),rating,containsSpoilers,imageUrl,upvoteCount,commentCount,
 * upvotedByMe,isMine,createdAt,source?} - source is omitted outside the feed.
 */
public record PostSummary(
        String id,
        UserSummary author,
        PostMovie movie,
        String title,
        String bodyPreview,
        int rating,
        @JsonProperty("containsSpoilers") boolean containsSpoilers,
        String imageUrl,
        long upvoteCount,
        long commentCount,
        @JsonProperty("upvotedByMe") boolean upvotedByMe,
        @JsonProperty("isMine") boolean isMine,
        Instant createdAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) FeedSource source
) {
    public PostSummary withSource(FeedSource s) {
        return new PostSummary(id, author, movie, title, bodyPreview, rating, containsSpoilers, imageUrl,
                upvoteCount, commentCount, upvotedByMe, isMine, createdAt, s);
    }
}

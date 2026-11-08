package com.cinecircle.comment.dto;

import com.cinecircle.user.dto.UserSummary;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/** {id,author,body,deleted,createdAt,isMine,replies} - deleted comments have body "[deleted]". */
public record CommentDto(
        String id,
        UserSummary author,
        String body,
        @JsonProperty("deleted") boolean deleted,
        Instant createdAt,
        @JsonProperty("isMine") boolean isMine,
        List<CommentDto> replies
) {
}

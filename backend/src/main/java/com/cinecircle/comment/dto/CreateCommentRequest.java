package com.cinecircle.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommentRequest(
        @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters") String body,
        String parentCommentId
) {
}

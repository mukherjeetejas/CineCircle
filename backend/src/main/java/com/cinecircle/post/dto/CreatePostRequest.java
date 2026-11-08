package com.cinecircle.post.dto;

import com.cinecircle.post.ImageType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** JSON part {@code post} of POST /posts. imageType defaults to NONE when omitted. */
public record CreatePostRequest(
        @NotNull(message = "is required") @Positive(message = "must be a TMDB movie id") Long tmdbId,
        @NotBlank(message = "is required") @Size(max = 120, message = "must be at most 120 characters") String title,
        @NotBlank(message = "is required") @Size(max = 5000, message = "must be at most 5000 characters") String body,
        @NotNull(message = "is required") @Min(value = 1, message = "must be between 1 and 10")
        @Max(value = 10, message = "must be between 1 and 10") Integer rating,
        Boolean containsSpoilers,
        ImageType imageType
) {
    public CreatePostRequest {
        title = title == null ? null : title.trim();
        if (imageType == null) {
            imageType = ImageType.NONE;
        }
        if (containsSpoilers == null) {
            containsSpoilers = false;
        }
    }
}

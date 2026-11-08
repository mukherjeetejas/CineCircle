package com.cinecircle.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH /users/me {displayName?,bio?,isPrivate?} - null fields are left unchanged. */
public record UpdateMeRequest(
        @Size(min = 1, max = 50, message = "must be 1-50 characters")
        @Pattern(regexp = ".*\\S.*", message = "must not be blank")
        String displayName,
        @Size(max = 160, message = "must be at most 160 characters")
        String bio,
        @JsonProperty("isPrivate") Boolean isPrivate
) {
}

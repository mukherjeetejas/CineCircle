package com.cinecircle.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Username and email are trimmed and lower-cased before validation. */
public record RegisterRequest(
        @NotBlank(message = "is required")
        @Pattern(regexp = "^[a-z0-9_]{3,20}$", message = "must be 3-20 characters: a-z, 0-9 or _")
        String username,
        @NotBlank(message = "is required") @Email(message = "must be a valid email") @Size(max = 254)
        String email,
        @NotBlank(message = "is required") @Size(min = 8, max = 100, message = "must be 8-100 characters")
        String password,
        @NotBlank(message = "is required") @Size(max = 50, message = "must be at most 50 characters")
        String displayName
) {
    public RegisterRequest {
        username = username == null ? null : username.trim().toLowerCase();
        email = email == null ? null : email.trim().toLowerCase();
        displayName = displayName == null ? null : displayName.strip();
    }
}

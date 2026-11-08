package com.cinecircle.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "is required") String usernameOrEmail,
        @NotBlank(message = "is required") String password
) {
}

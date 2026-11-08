package com.cinecircle.auth.dto;

import com.cinecircle.user.dto.UserProfile;

/** {token, user} */
public record AuthResponse(String token, UserProfile user) {
}

package com.cinecircle.security;

/** Authenticated principal extracted from the JWT. */
public record AuthUser(String id, String username) {

    /** Null-safe viewer id for optional-auth endpoints. */
    public static String idOf(AuthUser user) {
        return user == null ? null : user.id();
    }
}

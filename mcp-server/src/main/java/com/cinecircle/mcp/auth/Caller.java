package com.cinecircle.mcp.auth;

import org.springframework.http.HttpHeaders;

/**
 * Who is making ONE tool call. Created fresh for every call by {@link CallerResolver} and passed explicitly down to
 * the API client; never stored in a field, static or thread-local, so tokens cannot leak between callers.
 *
 * @param token    the CineCircle JWT to forward as {@code Authorization: Bearer}, or null (read-only, public access)
 * @param clientIp the caller's IP for rate limiting (HTTP only), or null
 * @param http     true when the call came in over HTTP (hosted / local HTTP), false for stdio or direct calls
 */
public record Caller(String token, String clientIp, boolean http) {

    /** A local (stdio / direct) caller. */
    public static Caller local(String token) {
        return new Caller(token, null, false);
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    /** Adds {@code Authorization: Bearer <token>} when this caller has a token. */
    public void applyTo(HttpHeaders headers) {
        if (hasToken()) {
            headers.setBearerAuth(token);
        }
    }

    /** Never include the token. */
    @Override
    public String toString() {
        return "Caller[token=" + (hasToken() ? "<set>" : "<none>") + ", clientIp=" + clientIp + ", http=" + http + "]";
    }
}

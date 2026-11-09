package com.cinecircle.common;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Client IP for per-IP rate limits (DEPLOY.md): the first entry of {@code X-Forwarded-For} if present, else the
 * remote address. (With {@code server.forward-headers-strategy: framework} Spring may already have moved that first
 * entry into the remote address and removed the header - the result is the same.)
 */
public final class ClientIp {

    private ClientIp() {
    }

    public static String of(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null) {
            String first = xff.split(",", 2)[0].strip();
            if (!first.isEmpty()) {
                return first;
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }
}

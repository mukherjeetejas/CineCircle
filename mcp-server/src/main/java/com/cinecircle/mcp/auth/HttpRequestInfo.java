package com.cinecircle.mcp.auth;

import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpTransportContextExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Map;

/**
 * What we keep from ONE incoming MCP HTTP request: the bearer token from its {@code Authorization} header (if any)
 * and the client IP. Stored in that request's {@link McpTransportContext}, which the MCP SDK hands to the tool call
 * through a per-request {@code McpSyncServerExchange}.
 *
 * @param bearerToken token from {@code Authorization: Bearer <jwt>}, or null
 * @param clientIp    first entry of {@code X-Forwarded-For}, else the remote address
 */
public record HttpRequestInfo(String bearerToken, String clientIp) {

    /** Key in {@link McpTransportContext}. */
    public static final String CONTEXT_KEY = "cinecircle.httpRequest";

    /** The transport context extractor for the Streamable HTTP transport (WebMvc {@link ServerRequest}). */
    public static final McpTransportContextExtractor<ServerRequest> EXTRACTOR =
            request -> McpTransportContext.create(Map.of(CONTEXT_KEY, from(request)));

    public static HttpRequestInfo from(ServerRequest request) {
        HttpHeaders headers = request.headers().asHttpHeaders();
        String remote = request.servletRequest().getRemoteAddr();
        return new HttpRequestInfo(bearer(headers.getFirst(HttpHeaders.AUTHORIZATION)),
                clientIp(headers.getFirst("X-Forwarded-For"), remote));
    }

    /** {@code "Bearer abc"} → {@code "abc"}; anything else (missing, blank, other scheme) → null. */
    static String bearer(String authorization) {
        if (authorization == null) {
            return null;
        }
        String v = authorization.trim();
        if (v.length() <= 7 || !v.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        String token = v.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    /** Same rule as the backend: first entry of {@code X-Forwarded-For} if present, else the remote address. */
    static String clientIp(String forwardedFor, String remoteAddr) {
        if (forwardedFor != null) {
            String first = forwardedFor.split(",", 2)[0].trim();
            if (!first.isEmpty()) {
                return first;
            }
        }
        return remoteAddr == null || remoteAddr.isBlank() ? "unknown" : remoteAddr;
    }

    /** Never include the token. */
    @Override
    public String toString() {
        return "HttpRequestInfo[bearerToken=" + (bearerToken != null ? "<set>" : "<none>") + ", clientIp=" + clientIp + "]";
    }
}

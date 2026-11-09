package com.cinecircle.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * {@code cinecircle.*} settings. Every value comes from an env var with a local default (see application.yml).
 *
 * @param apiUrl             CineCircle REST API base URL ({@code CINECIRCLE_API_URL}, default {@code http://localhost:8080/api})
 * @param token              optional user JWT ({@code CINECIRCLE_TOKEN}); only used when {@code allowEnvToken}.
 *                           Never logged, never in tool output.
 * @param webUrl             web app base URL used for links in tool output ({@code CINECIRCLE_WEB_URL})
 * @param allowEnvToken      {@code MCP_ALLOW_ENV_TOKEN} (default true): whether {@code CINECIRCLE_TOKEN} may be used.
 *                           Must be false on a shared/hosted server so callers never act as the operator.
 * @param rateLimitPerMinute {@code MCP_RATE_LIMIT_PER_MINUTE} (default 60): tool calls per client IP per minute
 *                           (HTTP only); 0 or less disables the limit
 * @param retryDelay         wait before the single retry when the backend looks asleep (default 5 s)
 */
@ConfigurationProperties("cinecircle")
public record CineCircleProperties(String apiUrl, String token, String webUrl, Boolean allowEnvToken,
                                   Integer rateLimitPerMinute, Duration retryDelay) {

    public static final String DEFAULT_API_URL = "http://localhost:8080/api";
    public static final String DEFAULT_WEB_URL = "http://localhost:5173";
    public static final int DEFAULT_RATE_LIMIT_PER_MINUTE = 60;
    public static final Duration DEFAULT_RETRY_DELAY = Duration.ofSeconds(5);

    public CineCircleProperties {
        apiUrl = stripTrailingSlash(isBlank(apiUrl) ? DEFAULT_API_URL : apiUrl.trim());
        webUrl = stripTrailingSlash(isBlank(webUrl) ? DEFAULT_WEB_URL : webUrl.trim());
        token = isBlank(token) ? null : token.trim();
        allowEnvToken = allowEnvToken == null || allowEnvToken;
        rateLimitPerMinute = rateLimitPerMinute == null ? DEFAULT_RATE_LIMIT_PER_MINUTE : rateLimitPerMinute;
        retryDelay = retryDelay == null || retryDelay.isNegative() ? DEFAULT_RETRY_DELAY : retryDelay;
    }

    /** Local defaults for everything except the three given values (handy for tests). */
    public static CineCircleProperties of(String apiUrl, String token, String webUrl) {
        return new CineCircleProperties(apiUrl, token, webUrl, null, null, null);
    }

    /** The env token if it is set AND allowed ({@code MCP_ALLOW_ENV_TOKEN=true}), else null. */
    public String usableEnvToken() {
        return Boolean.TRUE.equals(allowEnvToken) ? token : null;
    }

    public boolean hasUsableEnvToken() {
        return usableEnvToken() != null;
    }

    /** Never include the token (records print all components by default). */
    @Override
    public String toString() {
        return "CineCircleProperties[apiUrl=" + apiUrl + ", webUrl=" + webUrl
                + ", token=" + (token != null ? "<set>" : "<not set>") + ", allowEnvToken=" + allowEnvToken
                + ", rateLimitPerMinute=" + rateLimitPerMinute + ", retryDelay=" + retryDelay + "]";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String stripTrailingSlash(String s) {
        String r = s;
        while (r.endsWith("/")) {
            r = r.substring(0, r.length() - 1);
        }
        return r;
    }
}

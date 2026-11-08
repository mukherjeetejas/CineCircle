package com.cinecircle.mcp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code cinecircle.*} settings.
 *
 * @param apiUrl CineCircle REST API base URL ({@code CINECIRCLE_API_URL}, default {@code http://localhost:8080/api})
 * @param token  optional user JWT ({@code CINECIRCLE_TOKEN}); sent as Bearer. Never logged, never in tool output.
 * @param webUrl web app base URL used for links in tool output
 */
@ConfigurationProperties("cinecircle")
public record CineCircleProperties(String apiUrl, String token, String webUrl) {

    public static final String DEFAULT_API_URL = "http://localhost:8080/api";
    public static final String DEFAULT_WEB_URL = "http://localhost:5173";

    public CineCircleProperties {
        apiUrl = stripTrailingSlash(isBlank(apiUrl) ? DEFAULT_API_URL : apiUrl.trim());
        webUrl = stripTrailingSlash(isBlank(webUrl) ? DEFAULT_WEB_URL : webUrl.trim());
        token = isBlank(token) ? null : token.trim();
    }

    public boolean hasToken() {
        return token != null;
    }

    /** Never include the token (records print all components by default). */
    @Override
    public String toString() {
        return "CineCircleProperties[apiUrl=" + apiUrl + ", webUrl=" + webUrl
                + ", token=" + (hasToken() ? "<set>" : "<not set>") + "]";
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

package com.cinecircle.mcp.error;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Maps backend errors to short tool error texts that always include the backend {@code code}. */
public final class ErrorMessages {

    /** Write tool without a token, local (stdio / direct) use. */
    public static final String TOKEN_REQUIRED = "Set CINECIRCLE_TOKEN to post as your account.";

    /** Write tool without a token over HTTP (hosted mode). */
    public static final String TOKEN_REQUIRED_HOSTED = "Add your CineCircle token: see Settings → Use CineCircle with Claude";

    public static final String WAKING_UP = "CineCircle is waking up — try again in a minute.";

    public static String rateLimited(int limitPerMinute, long retryAfterSeconds) {
        return "CineCircle error RATE_LIMITED: Slow down — too many requests from your address (limit "
                + limitPerMinute + " per minute). Try again in " + retryAfterSeconds + " seconds.";
    }

    public static String forWakingUp(BackendWakingUpException e) {
        return WAKING_UP + " (CineCircle API at " + e.apiUrl() + ": " + e.detail() + ")";
    }

    private ErrorMessages() {
    }

    public static String forApiError(CineCircleApiException e) {
        StringBuilder sb = new StringBuilder("CineCircle error ").append(e.code());
        String msg = e.getMessage();
        if (msg != null && !msg.isBlank()) {
            sb.append(": ").append(oneLine(msg, 300));
        }
        if (!e.fieldErrors().isEmpty()) {
            sb.append(" (").append(new TreeMap<>(e.fieldErrors()).entrySet().stream()
                    .map(en -> en.getKey() + ": " + oneLine(en.getValue(), 100))
                    .collect(Collectors.joining("; "))).append(')');
        }
        String hint = hint(e.code());
        if (hint != null) {
            sb.append(sb.charAt(sb.length() - 1) == '.' ? " " : ". ").append(hint);
        }
        return sb.toString();
    }

    public static String forUnreachable(ApiUnreachableException e) {
        return "CineCircle API is not reachable at " + e.apiUrl();
    }

    private static final Map<String, String> HINTS = Map.of(
            "MOVIE_NOT_FOUND", "Use search_movies to find a valid tmdbId; never guess one.",
            "NOT_ENOUGH_REVIEWS", "A summary needs at least 3 public, spoiler-free reviews of this movie.",
            "UNAUTHORIZED", "Your CineCircle token is missing, invalid or expired (tokens last 7 days); get a new one "
                    + "in Settings → Use CineCircle with Claude (CINECIRCLE_TOKEN or the Authorization header).",
            "PRIVATE_ACCOUNT", "This account is private; only approved followers can see its reviews.",
            "SUMMARY_UNAVAILABLE", "The AI summary is not available right now; try get_movie_reviews instead.",
            "TMDB_UNAVAILABLE", "The movie catalogue (TMDB) is not available right now; try again later.");

    private static String hint(String code) {
        return code == null ? null : HINTS.get(code);
    }

    private static String oneLine(String s, int max) {
        String flat = s.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "…";
    }
}

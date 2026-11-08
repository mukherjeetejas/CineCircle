package com.cinecircle.mcp.error;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Maps backend errors to short tool error texts that always include the backend {@code code}. */
public final class ErrorMessages {

    public static final String TOKEN_REQUIRED = "Set CINECIRCLE_TOKEN to post as your account.";

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
            "UNAUTHORIZED", "CINECIRCLE_TOKEN is missing, invalid or expired (tokens last 7 days); get a new one.",
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

package com.cinecircle.mcp.tools;

import java.util.Locale;
import java.util.Set;

/** Argument normalisation / validation shared by the tool classes. */
final class ToolArgs {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;

    private ToolArgs() {
    }

    static long tmdbId(Long tmdbId) {
        if (tmdbId == null || tmdbId <= 0) {
            throw ToolRunner.invalid("tmdbId must be a positive TMDB movie id. Use search_movies to find one.");
        }
        return tmdbId;
    }

    /** limit clamped to 1-20, default 10. */
    static int limit(Integer limit) {
        if (limit == null) {
            return DEFAULT_LIMIT;
        }
        return Math.max(1, Math.min(MAX_LIMIT, limit));
    }

    static String oneOf(String name, String value, String defaultValue, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(v)) {
            throw ToolRunner.invalid(name + " must be one of " + String.join(", ", allowed.stream().sorted().toList())
                    + ".");
        }
        return v;
    }

    static String required(String name, String value) {
        if (value == null || value.isBlank()) {
            throw ToolRunner.invalid(name + " is required.");
        }
        return value.trim();
    }
}

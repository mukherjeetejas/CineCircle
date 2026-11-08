package com.cinecircle.summary;

import java.time.Instant;
import java.util.List;

/**
 * {@code ReviewSummary}: {tmdbId, summary, pros[], cons[], sentiment, avgRating, basedOnCount, generatedAt, stale}.
 * {@code stale} is true only when a failed regeneration fell back to an older cached copy.
 */
public record ReviewSummary(
        long tmdbId,
        String summary,
        List<String> pros,
        List<String> cons,
        Sentiment sentiment,
        Double avgRating,
        int basedOnCount,
        Instant generatedAt,
        boolean stale
) {
    public static ReviewSummary of(MovieReviewSummary s, boolean stale) {
        return new ReviewSummary(s.getTmdbId(), s.getSummary(),
                s.getPros() == null ? List.of() : s.getPros(),
                s.getCons() == null ? List.of() : s.getCons(),
                s.getSentiment(), s.getAvgRating(), s.getBasedOnCount(), s.getGeneratedAt(), stale);
    }
}

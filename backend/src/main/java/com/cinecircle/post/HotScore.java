package com.cinecircle.post;

import java.time.Instant;

/** Reddit-like hot score: {@code log10(max(upvoteCount,1)) + createdAtEpochSeconds / 45000}. */
public final class HotScore {

    private HotScore() {
    }

    public static double compute(long upvoteCount, Instant createdAt) {
        return Math.log10(Math.max(upvoteCount, 1)) + createdAt.getEpochSecond() / 45000.0;
    }
}

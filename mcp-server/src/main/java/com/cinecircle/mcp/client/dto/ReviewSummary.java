package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.List;

/** {@code {tmdbId,summary,pros[],cons[],sentiment,avgRating,basedOnCount,generatedAt,stale}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReviewSummary(
        long tmdbId,
        String summary,
        List<String> pros,
        List<String> cons,
        String sentiment,
        Double avgRating,
        int basedOnCount,
        Instant generatedAt,
        boolean stale
) {
}

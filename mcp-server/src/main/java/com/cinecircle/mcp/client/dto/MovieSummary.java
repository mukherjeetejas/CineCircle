package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code {tmdbId,title,releaseYear,posterUrl,tmdbVoteAverage,reviewCount,avgRating}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieSummary(
        long tmdbId,
        String title,
        Integer releaseYear,
        String posterUrl,
        Double tmdbVoteAverage,
        long reviewCount,
        Double avgRating
) {
}

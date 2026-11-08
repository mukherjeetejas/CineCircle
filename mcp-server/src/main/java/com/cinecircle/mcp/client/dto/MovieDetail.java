package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/** MovieSummary + {@code {overview,backdropUrl,genres}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieDetail(
        long tmdbId,
        String title,
        Integer releaseYear,
        String posterUrl,
        Double tmdbVoteAverage,
        long reviewCount,
        Double avgRating,
        String overview,
        String backdropUrl,
        List<String> genres
) {
}

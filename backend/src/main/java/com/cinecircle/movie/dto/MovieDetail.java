package com.cinecircle.movie.dto;

import java.util.List;

/** MovieSummary + {overview,backdropUrl,genres} */
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

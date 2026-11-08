package com.cinecircle.movie.dto;

/** {tmdbId,title,releaseYear,posterUrl,tmdbVoteAverage,reviewCount,avgRating} */
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

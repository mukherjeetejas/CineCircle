package com.cinecircle.movie.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Subset of TMDB v3 JSON payloads we consume. */
public final class TmdbModels {

    private TmdbModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Genre(Long id, String name) {
    }

    /** Item in /search/movie and /trending/movie/week results. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieResult(
            Long id,
            String title,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("release_date") String releaseDate,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("vote_average") Double voteAverage
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PagedResults(
            Integer page,
            List<MovieResult> results,
            @JsonProperty("total_pages") Integer totalPages,
            @JsonProperty("total_results") Integer totalResults
    ) {
    }

    /** /movie/{id} */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MovieDetails(
            Long id,
            String title,
            @JsonProperty("original_title") String originalTitle,
            @JsonProperty("release_date") String releaseDate,
            String overview,
            @JsonProperty("poster_path") String posterPath,
            @JsonProperty("backdrop_path") String backdropPath,
            @JsonProperty("vote_average") Double voteAverage,
            List<Genre> genres
    ) {
    }

    /** Parses the year out of TMDB's "YYYY-MM-DD" (may be empty). */
    public static Integer yearOf(String releaseDate) {
        if (releaseDate == null || releaseDate.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(releaseDate.substring(0, 4));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

package com.cinecircle.movie;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** TMDB cache entry. Refreshed when older than 7 days. */
@Document("movies")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Movie {

    @Id
    private String id;

    @Indexed(unique = true)
    private Long tmdbId;

    private String title;
    private String originalTitle;
    private Integer releaseYear;
    private String overview;
    private String posterPath;
    private String backdropPath;
    private List<String> genres;
    private Double tmdbVoteAverage;
    private Instant cachedAt;
}

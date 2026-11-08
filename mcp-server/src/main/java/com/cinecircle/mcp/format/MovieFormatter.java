package com.cinecircle.mcp.format;

import com.cinecircle.mcp.client.dto.MovieDetail;
import com.cinecircle.mcp.client.dto.MovieSummary;
import org.springframework.stereotype.Component;

import java.util.List;

/** Movies as compact text. */
@Component
public class MovieFormatter {

    /** Upper bound on movies listed by search / trending. */
    public static final int MAX_MOVIES = 20;

    private final Links links;

    public MovieFormatter(Links links) {
        this.links = links;
    }

    public String searchResults(String query, List<MovieSummary> movies) {
        if (movies.isEmpty()) {
            return "No movies found for \"" + Text.clip(query, 100) + "\".";
        }
        StringBuilder sb = new StringBuilder("Movies matching \"").append(Text.clip(query, 100)).append("\" (use the tmdbId with other tools):\n");
        appendMovies(sb, movies);
        return sb.toString().stripTrailing();
    }

    public String trending(List<MovieSummary> movies) {
        if (movies.isEmpty()) {
            return "No trending movies right now.";
        }
        StringBuilder sb = new StringBuilder("Trending movies this week:\n");
        appendMovies(sb, movies);
        return sb.toString().stripTrailing();
    }

    public String detail(MovieDetail m) {
        StringBuilder sb = new StringBuilder()
                .append(m.title()).append(Text.year(m.releaseYear())).append(" · tmdbId ").append(m.tmdbId()).append('\n');
        if (m.genres() != null && !m.genres().isEmpty()) {
            sb.append("Genres: ").append(String.join(", ", m.genres())).append('\n');
        }
        sb.append("TMDB rating: ").append(Text.decimal(m.tmdbVoteAverage())).append("/10 · ")
                .append(cineCircleStats(m.reviewCount(), m.avgRating())).append('\n');
        if (!Text.isBlank(m.overview())) {
            sb.append("Overview: ").append(Text.clip(m.overview(), Text.MAX_OVERVIEW)).append('\n');
        }
        sb.append("Link: ").append(links.movie(m.tmdbId()));
        return sb.toString();
    }

    private static void appendMovies(StringBuilder sb, List<MovieSummary> movies) {
        movies.stream().limit(MAX_MOVIES).forEach(m -> sb
                .append("- ").append(m.title()).append(Text.year(m.releaseYear()))
                .append(" · tmdbId ").append(m.tmdbId())
                .append(" · TMDB ").append(Text.decimal(m.tmdbVoteAverage()))
                .append(" · ").append(cineCircleStats(m.reviewCount(), m.avgRating()))
                .append('\n'));
    }

    private static String cineCircleStats(long reviewCount, Double avgRating) {
        if (reviewCount <= 0) {
            return "no CineCircle reviews yet";
        }
        return "CineCircle: " + Text.plural(reviewCount, "review", "reviews") + ", avg " + Text.decimal(avgRating) + "/10";
    }
}

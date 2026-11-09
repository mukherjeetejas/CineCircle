package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.client.CineCircleApiClient;
import com.cinecircle.mcp.client.dto.MovieSummary;
import com.cinecircle.mcp.format.MovieFormatter;
import com.cinecircle.mcp.format.PlainTextResultConverter;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/** Movie tools: search_movies, get_trending_movies, get_movie. */
@Component
public class MovieTools {

    private final CineCircleApiClient api;
    private final MovieFormatter format;
    private final ToolRunner runner;

    public MovieTools(CineCircleApiClient api, MovieFormatter format, ToolRunner runner) {
        this.api = api;
        this.format = format;
        this.runner = runner;
    }

    @Tool(name = "search_movies", resultConverter = PlainTextResultConverter.class, description = """
            Search the CineCircle movie catalogue (backed by TMDB) by title. Returns up to 20 movies with their \
            tmdbId, release year, TMDB rating and CineCircle review count / average rating. Use this first \
            whenever you need a movie's tmdbId (for get_movie, get_movie_reviews, get_review_summary or \
            create_review); never guess a tmdbId.""")
    public String searchMovies(
            @ToolParam(description = "Movie title or part of it, e.g. \"dune\" or \"the godfather\"") String query,
            ToolContext toolContext) {
        return runner.read("search_movies", toolContext, caller -> {
            String q = ToolArgs.required("query", query);
            return format.searchResults(q, api.searchMovies(caller, q).itemsOrEmpty());
        });
    }

    @Tool(name = "get_trending_movies", resultConverter = PlainTextResultConverter.class, description = """
            List the movies trending this week (from TMDB), with tmdbId, year, TMDB rating and CineCircle review \
            stats. Use this when the user asks what is popular or new, or wants something to watch.""")
    public String getTrendingMovies(ToolContext toolContext) {
        return runner.read("get_trending_movies", toolContext, caller -> {
            List<MovieSummary> movies = api.trendingMovies(caller);
            return format.trending(movies == null ? List.of() : movies);
        });
    }

    @Tool(name = "get_movie", resultConverter = PlainTextResultConverter.class, description = """
            Get details of one movie by tmdbId: title, year, genres, overview, TMDB rating and the number and \
            average rating of CineCircle reviews. Use search_movies first if you do not know the tmdbId.""")
    public String getMovie(@ToolParam(description = "TMDB movie id, from search_movies") Long tmdbId,
            ToolContext toolContext) {
        return runner.read("get_movie", toolContext, caller -> format.detail(api.movie(caller, ToolArgs.tmdbId(tmdbId))));
    }
}

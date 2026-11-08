package com.cinecircle.movie;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.common.PageResponse;
import com.cinecircle.common.UrlBuilder;
import com.cinecircle.movie.dto.MovieDetail;
import com.cinecircle.movie.dto.MovieSummary;
import com.cinecircle.movie.tmdb.TmdbClient;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.movie.tmdb.TmdbUnavailableException;
import com.cinecircle.post.ReviewStats;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MovieService {

    static final Duration DETAIL_TTL = Duration.ofDays(7);
    static final Duration TRENDING_TTL = Duration.ofHours(1);
    /** TMDB refuses pages above 500. */
    private static final int TMDB_MAX_PAGE = 500;

    private final TmdbClient tmdb;
    private final MovieRepository movies;
    private final ReviewStats reviewStats;
    private final UrlBuilder urls;

    private final Clock clock = Clock.systemUTC();
    private volatile TrendingCache trendingCache;

    private record TrendingCache(List<TmdbModels.MovieResult> results, Instant fetchedAt) {
    }

    // ------------------------------------------------------------------ cache

    /**
     * Returns the cached movie (refreshing it from TMDB if older than 7 days), or fetches and caches it.
     * Empty when TMDB does not know the id. If TMDB is down, a stale cache entry is served; with no
     * cache entry {@link TmdbUnavailableException} propagates.
     */
    public Optional<Movie> getOrFetch(long tmdbId) {
        if (tmdbId <= 0) {
            return Optional.empty();
        }
        Optional<Movie> cached = movies.findByTmdbId(tmdbId);
        if (cached.isPresent() && cached.get().getCachedAt() != null
                && cached.get().getCachedAt().isAfter(clock.instant().minus(DETAIL_TTL))) {
            return cached;
        }
        Optional<TmdbModels.MovieDetails> fetched;
        try {
            fetched = tmdb.getMovie(tmdbId);
        } catch (TmdbUnavailableException e) {
            if (cached.isPresent()) {
                log.warn("TMDB unavailable, serving stale cache for movie {}", tmdbId);
                return cached;
            }
            throw e;
        }
        if (fetched.isEmpty()) {
            return Optional.empty();
        }
        Movie movie = cached.orElseGet(Movie::new);
        apply(movie, fetched.get());
        try {
            return Optional.of(movies.save(movie));
        } catch (DuplicateKeyException e) {
            // concurrent first fetch of the same movie
            return movies.findByTmdbId(tmdbId);
        }
    }

    private void apply(Movie m, TmdbModels.MovieDetails d) {
        m.setTmdbId(d.id());
        m.setTitle(d.title());
        m.setOriginalTitle(d.originalTitle());
        m.setReleaseYear(TmdbModels.yearOf(d.releaseDate()));
        m.setOverview(d.overview());
        m.setPosterPath(d.posterPath());
        m.setBackdropPath(d.backdropPath());
        m.setGenres(d.genres() == null ? List.of()
                : d.genres().stream().map(TmdbModels.Genre::name).filter(Objects::nonNull).toList());
        m.setTmdbVoteAverage(d.voteAverage());
        m.setCachedAt(clock.instant());
    }

    // ------------------------------------------------------------------ API

    public MovieDetail detail(long tmdbId) {
        Movie m = getOrFetch(tmdbId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MOVIE_NOT_FOUND, "Movie not found on TMDB", null));
        ReviewStats.Stats s = reviewStats.forMovie(m.getTmdbId());
        return new MovieDetail(m.getTmdbId(), m.getTitle(), m.getReleaseYear(), urls.poster(m.getPosterPath()),
                m.getTmdbVoteAverage(), s.reviewCount(), s.avgRating(), m.getOverview(),
                urls.backdrop(m.getBackdropPath()), m.getGenres() == null ? List.of() : m.getGenres());
    }

    /** Proxied TMDB search; page is 0-based (TMDB page = page + 1), TMDB returns 20 per page. */
    public PageResponse<MovieSummary> search(String q, int page) {
        int p = Math.max(page, 0);
        if (q == null || q.isBlank()) {
            return new PageResponse<>(List.of(), p, 20, false);
        }
        int tmdbPage = Math.min(p + 1, TMDB_MAX_PAGE);
        TmdbModels.PagedResults res = tmdb.searchMovies(q.trim(), tmdbPage);
        List<TmdbModels.MovieResult> results = res.results() == null ? List.of() : res.results();
        int totalPages = res.totalPages() == null ? 0 : res.totalPages();
        boolean hasNext = tmdbPage < Math.min(totalPages, TMDB_MAX_PAGE);
        return new PageResponse<>(toSummaries(results), p, 20, hasNext);
    }

    /** TMDB trending/week, cached in memory for 1 hour (stale copy served if TMDB is down). */
    public List<MovieSummary> trending() {
        return toSummaries(trendingResults());
    }

    /** Raw trending list (also used by the seeder). */
    public List<TmdbModels.MovieResult> trendingResults() {
        TrendingCache cache = trendingCache;
        if (cache != null && cache.fetchedAt().isAfter(clock.instant().minus(TRENDING_TTL))) {
            return cache.results();
        }
        try {
            List<TmdbModels.MovieResult> fresh = tmdb.trendingWeek();
            trendingCache = new TrendingCache(fresh, clock.instant());
            return fresh;
        } catch (TmdbUnavailableException e) {
            if (cache != null) {
                log.warn("TMDB unavailable, serving stale trending list");
                return cache.results();
            }
            throw e;
        }
    }

    private List<MovieSummary> toSummaries(List<TmdbModels.MovieResult> results) {
        List<Long> ids = results.stream().map(TmdbModels.MovieResult::id).filter(Objects::nonNull).toList();
        Map<Long, ReviewStats.Stats> stats = reviewStats.forMovies(ids);
        return results.stream()
                .filter(r -> r.id() != null)
                .map(r -> {
                    ReviewStats.Stats s = stats.getOrDefault(r.id(), ReviewStats.Stats.EMPTY);
                    return new MovieSummary(r.id(), r.title(), TmdbModels.yearOf(r.releaseDate()),
                            urls.poster(r.posterPath()), r.voteAverage(), s.reviewCount(), s.avgRating());
                })
                .toList();
    }
}

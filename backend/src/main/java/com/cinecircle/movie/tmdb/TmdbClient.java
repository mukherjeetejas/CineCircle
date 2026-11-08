package com.cinecircle.movie.tmdb;

import com.cinecircle.config.AppProperties;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.PrematureCloseException;
import reactor.netty.resources.ConnectionProvider;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Blocking facade over TMDB v3 using WebClient and Bearer (v4 read token) auth.
 *
 * <p>Resilience against networks that silently drop idle/new connections:
 * <ul>
 *   <li>a dedicated connection pool evicts idle connections after 15 s (max life 5 min, background
 *       eviction every 30 s) so stale pooled sockets are not reused;</li>
 *   <li>transport-level failures only ({@link WebClientRequestException}, or an {@link IOException} /
 *       {@link PrematureCloseException} in the cause chain) are retried twice with 200 ms / 400 ms backoff.
 *       HTTP status responses are never retried, so 404 still means "unknown movie";</li>
 *   <li>each attempt is bounded by the configured timeout (5 s) and the whole call by 12 s.</li>
 * </ul>
 * Any remaining transport failure or non-404 error status surfaces as {@link TmdbUnavailableException} (502).
 */
@Slf4j
@Component
public class TmdbClient implements DisposableBean {

    static final int MAX_RETRIES = 2;
    static final Duration FIRST_BACKOFF = Duration.ofMillis(200);
    static final Duration MAX_BACKOFF = Duration.ofMillis(400);
    static final Duration TOTAL_TIMEOUT = Duration.ofSeconds(12);

    private final WebClient webClient;
    private final ConnectionProvider connectionProvider;
    private final Duration attemptTimeout;
    private final Duration totalTimeout;
    private final RetryBackoffSpec retrySpec;
    private final boolean configured;

    public TmdbClient(WebClient.Builder builder, AppProperties props) {
        AppProperties.Tmdb cfg = props.tmdb();
        this.attemptTimeout = cfg.timeout() == null ? Duration.ofSeconds(5) : cfg.timeout();
        this.totalTimeout = TOTAL_TIMEOUT;
        this.configured = cfg.token() != null && !cfg.token().isBlank();
        if (!configured) {
            log.warn("TMDB_API_TOKEN is not set: movie endpoints will return 502 TMDB_UNAVAILABLE and "
                    + "seeding will skip posts. Set TMDB_API_TOKEN (TMDB v4 read access token) to enable TMDB.");
        }

        this.connectionProvider = ConnectionProvider.builder("tmdb")
                .maxConnections(50)
                .maxIdleTime(Duration.ofSeconds(15))
                .maxLifeTime(Duration.ofMinutes(5))
                .evictInBackground(Duration.ofSeconds(30))
                .pendingAcquireTimeout(attemptTimeout)
                .build();
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) attemptTimeout.toMillis())
                .responseTimeout(attemptTimeout);

        this.retrySpec = Retry.backoff(MAX_RETRIES, FIRST_BACKOFF)
                .maxBackoff(MAX_BACKOFF)
                .jitter(0)
                .filter(TmdbClient::isTransient)
                .doBeforeRetry(s -> log.info("TMDB transport error ({}), retry {}/{}",
                        s.failure().toString(), s.totalRetries() + 1, MAX_RETRIES))
                // propagate the original error rather than Reactor's RetryExhaustedException
                .onRetryExhaustedThrow((spec, signal) -> signal.failure());

        // Note: if the supplied builder already has an exchangeFunction (tests), it takes precedence
        // over this connector.
        this.webClient = builder
                .baseUrl(cfg.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + (cfg.token() == null ? "" : cfg.token()))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    /** @return the movie, or empty if TMDB answers 404 (unknown id). */
    public Optional<TmdbModels.MovieDetails> getMovie(long tmdbId) {
        try {
            return Optional.ofNullable(call(webClient.get()
                    .uri("/movie/{id}?language=en-US", tmdbId)
                    .retrieve()
                    .bodyToMono(TmdbModels.MovieDetails.class)));
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return Optional.empty();
            }
            throw unavailable(e);
        }
    }

    /** @param tmdbPage 1-based TMDB page */
    public TmdbModels.PagedResults searchMovies(String query, int tmdbPage) {
        try {
            TmdbModels.PagedResults res = call(webClient.get()
                    .uri(uri -> uri.path("/search/movie")
                            .queryParam("query", query)
                            .queryParam("page", tmdbPage)
                            .queryParam("include_adult", false)
                            .queryParam("language", "en-US")
                            .build())
                    .retrieve()
                    .bodyToMono(TmdbModels.PagedResults.class));
            return res == null ? new TmdbModels.PagedResults(tmdbPage, List.of(), 0, 0) : res;
        } catch (WebClientResponseException e) {
            throw unavailable(e);
        }
    }

    public List<TmdbModels.MovieResult> trendingWeek() {
        try {
            TmdbModels.PagedResults res = call(webClient.get()
                    .uri("/trending/movie/week?language=en-US")
                    .retrieve()
                    .bodyToMono(TmdbModels.PagedResults.class));
            return res == null || res.results() == null ? List.of() : res.results();
        } catch (WebClientResponseException e) {
            throw unavailable(e);
        }
    }

    public boolean isConfigured() {
        return configured;
    }

    /**
     * Retry only failures where no HTTP response was received: connection reset / refused, premature close,
     * connect or read timeout at the transport level. Never retries an HTTP status response.
     */
    static boolean isTransient(Throwable t) {
        if (t instanceof WebClientResponseException) {
            return false;
        }
        if (t instanceof WebClientRequestException) {
            return true;
        }
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof IOException || c instanceof PrematureCloseException) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    private <T> T call(Mono<T> mono) {
        if (!configured) {
            // fail fast: no network round-trip without a token
            throw new TmdbUnavailableException("Movie service (TMDB) is not configured on this server");
        }
        try {
            // the request Mono is cold: each retry re-subscribes and performs a fresh exchange
            return mono.timeout(attemptTimeout)
                    .retryWhen(retrySpec)
                    .block(totalTimeout);
        } catch (WebClientResponseException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("TMDB request failed: {}", e.toString());
            throw new TmdbUnavailableException("Movie service (TMDB) is unavailable, please try again later");
        }
    }

    private TmdbUnavailableException unavailable(WebClientResponseException e) {
        log.warn("TMDB returned {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
        return new TmdbUnavailableException("Movie service (TMDB) is unavailable, please try again later");
    }

    @Override
    public void destroy() {
        connectionProvider.dispose();
    }
}

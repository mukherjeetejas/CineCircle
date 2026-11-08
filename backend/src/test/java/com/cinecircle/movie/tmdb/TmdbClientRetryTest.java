package com.cinecircle.movie.tmdb;

import com.cinecircle.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.SocketException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Retry behaviour of {@link TmdbClient}, driven by a scripted {@link ExchangeFunction} (no network). */
class TmdbClientRetryTest {

    private static final String MATRIX_JSON = """
            {"id":603,"title":"The Matrix","original_title":"The Matrix","release_date":"1999-03-30",
             "overview":"Neo","poster_path":"/m.jpg","backdrop_path":"/b.jpg","vote_average":8.2,
             "genres":[{"id":28,"name":"Action"}]}""";

    private final AtomicInteger calls = new AtomicInteger();

    /** Each exchange consumes the next scripted outcome (the last one repeats). */
    private TmdbClient client(List<Supplier<Mono<ClientResponse>>> script) {
        Deque<Supplier<Mono<ClientResponse>>> queue = new ArrayDeque<>(script);
        ExchangeFunction fn = request -> {
            calls.incrementAndGet();
            Supplier<Mono<ClientResponse>> next = queue.size() > 1 ? queue.poll() : queue.peek();
            return Mono.defer(next);
        };
        AppProperties props = new AppProperties("http://localhost:8080",
                new AppProperties.Tmdb("token", "http://tmdb.test/3", Duration.ofSeconds(5)),
                new AppProperties.Jwt("x".repeat(32), Duration.ofDays(7)), false,
                new AppProperties.Cors(List.of("http://localhost:5173")));
        return new TmdbClient(WebClient.builder().exchangeFunction(fn), props);
    }

    private static Supplier<Mono<ClientResponse>> reset() {
        return () -> Mono.error(new WebClientRequestException(new SocketException("Connection reset"),
                HttpMethod.GET, URI.create("http://tmdb.test/3/movie/603"), new HttpHeaders()));
    }

    private static Supplier<Mono<ClientResponse>> status(HttpStatus status, String body) {
        return () -> Mono.just(ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build());
    }

    @Test
    void connectionResetOnceThenSuccessIsRetried() {
        TmdbClient c = client(List.of(reset(), status(HttpStatus.OK, MATRIX_JSON)));

        var movie = c.getMovie(603);

        assertThat(movie).isPresent();
        assertThat(movie.get().title()).isEqualTo("The Matrix");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void notFoundIsNotRetriedAndMeansUnknownMovie() {
        TmdbClient c = client(List.of(status(HttpStatus.NOT_FOUND, "{\"status_code\":34}")));

        assertThat(c.getMovie(99_999_999)).isEmpty();
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void resetThenNotFoundStillMeansUnknownMovie() {
        // the case seen live: first call after idle hits a stale connection
        TmdbClient c = client(List.of(reset(), status(HttpStatus.NOT_FOUND, "{}")));

        assertThat(c.getMovie(99_999_999)).isEmpty();
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void serverErrorStatusIsNotRetried() {
        TmdbClient c = client(List.of(status(HttpStatus.SERVICE_UNAVAILABLE, "{}")));

        assertThatThrownBy(c::trendingWeek).isInstanceOf(TmdbUnavailableException.class);
        assertThat(calls.get()).isEqualTo(1);
    }

    @Test
    void persistentResetGivesUpAfterTwoRetries() {
        TmdbClient c = client(List.of(reset()));
        long start = System.nanoTime();

        assertThatThrownBy(() -> c.searchMovies("matrix", 1)).isInstanceOf(TmdbUnavailableException.class);

        assertThat(calls.get()).isEqualTo(3); // 1 attempt + 2 retries
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        assertThat(took).isGreaterThanOrEqualTo(Duration.ofMillis(550)); // 200 ms + 400 ms backoff (approx.)
        assertThat(took).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void transientClassification() {
        assertThat(TmdbClient.isTransient(new WebClientRequestException(new SocketException("reset"),
                HttpMethod.GET, URI.create("http://x"), new HttpHeaders()))).isTrue();
        assertThat(TmdbClient.isTransient(new RuntimeException(new IOException("broken pipe")))).isTrue();
        assertThat(TmdbClient.isTransient(new SocketException("Connection reset"))).isTrue();
        assertThat(TmdbClient.isTransient(WebClientResponseException.create(404, "Not Found", null, null, null)))
                .isFalse();
        assertThat(TmdbClient.isTransient(WebClientResponseException.create(500, "err", null, null, null)))
                .isFalse();
        assertThat(TmdbClient.isTransient(new TimeoutException())).isFalse();
        assertThat(TmdbClient.isTransient(new IllegalStateException("decode"))).isFalse();
    }
}

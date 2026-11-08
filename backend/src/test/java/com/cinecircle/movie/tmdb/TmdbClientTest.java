package com.cinecircle.movie.tmdb;

import com.cinecircle.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TmdbClientTest {

    private static TmdbClient client(String token) {
        AppProperties props = new AppProperties("http://localhost:8080",
                // unroutable port: if a request were attempted it would fail differently/slowly
                new AppProperties.Tmdb(token, "http://localhost:1/3", Duration.ofSeconds(5)),
                new AppProperties.Jwt("x".repeat(32), Duration.ofDays(7)), false,
                new AppProperties.Cors(List.of("http://localhost:5173")));
        return new TmdbClient(WebClient.builder(), props);
    }

    @Test
    void emptyTokenFailsFastWithTmdbUnavailable() {
        TmdbClient c = client("");
        assertThat(c.isConfigured()).isFalse();
        long start = System.nanoTime();
        assertThatThrownBy(() -> c.getMovie(603)).isInstanceOf(TmdbUnavailableException.class);
        assertThatThrownBy(c::trendingWeek).isInstanceOf(TmdbUnavailableException.class);
        assertThatThrownBy(() -> c.searchMovies("matrix", 1)).isInstanceOf(TmdbUnavailableException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void nullTokenIsTreatedAsUnconfigured() {
        assertThat(client(null).isConfigured()).isFalse();
        assertThat(client("abc").isConfigured()).isTrue();
    }
}

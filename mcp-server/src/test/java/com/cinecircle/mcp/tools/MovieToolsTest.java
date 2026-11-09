package com.cinecircle.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;

import java.net.ConnectException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class MovieToolsTest extends ToolTestSupport {

    private static final String DUNE = "{\"tmdbId\":438631,\"title\":\"Dune\",\"releaseYear\":2021,\"posterUrl\":null,"
            + "\"tmdbVoteAverage\":7.79,\"reviewCount\":3,\"avgRating\":8.333}";
    private static final String DUNE2 = "{\"tmdbId\":693134,\"title\":\"Dune: Part Two\",\"releaseYear\":2024,"
            + "\"posterUrl\":null,\"tmdbVoteAverage\":8.2,\"reviewCount\":0,\"avgRating\":null}";

    @Test
    void searchMovies_callsSearchFirstPage_encodesQuery_andListsTmdbIds() {
        setUp(null);
        server.expect(requestTo(API + "/movies/search?q=dune%20%26%20more&page=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("q", "dune%20%26%20more"))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess("{\"items\":[" + DUNE + "," + DUNE2 + "],\"page\":0,\"size\":20,\"hasNext\":true}",
                        MediaType.APPLICATION_JSON));

        String out = movieTools.searchMovies("  dune & more ", LOCAL);

        assertThat(out).contains("Movies matching \"dune & more\"")
                .contains("- Dune (2021) · tmdbId 438631 · TMDB 7.8 · CineCircle: 3 reviews, avg 8.3/10")
                .contains("- Dune: Part Two (2024) · tmdbId 693134 · TMDB 8.2 · no CineCircle reviews yet");
    }

    @Test
    void searchMovies_noResults() {
        setUp(null);
        server.expect(requestTo(API + "/movies/search?q=zzz&page=0"))
                .andRespond(withSuccess(page(false), MediaType.APPLICATION_JSON));

        assertThat(movieTools.searchMovies("zzz", LOCAL)).isEqualTo("No movies found for \"zzz\".");
    }

    @Test
    void searchMovies_blankQuery_isRejectedWithoutCallingTheBackend() {
        setUp(null);
        assertThat(toolError(() -> movieTools.searchMovies("  ", LOCAL)))
                .isEqualTo("CineCircle error VALIDATION_ERROR: query is required.");
    }

    @Test
    void searchMovies_tmdbDown_mapsErrorCode() {
        setUp(null);
        server.expect(requestTo(API + "/movies/search?q=dune&page=0"))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(502, "TMDB_UNAVAILABLE", "Movie catalogue unavailable")));

        assertThat(toolError(() -> movieTools.searchMovies("dune", LOCAL)))
                .startsWith("CineCircle error TMDB_UNAVAILABLE: Movie catalogue unavailable.");
    }

    @Test
    void getTrendingMovies_listsMovies() {
        setUp(null);
        server.expect(requestTo(API + "/movies/trending")).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[" + DUNE2 + "]", MediaType.APPLICATION_JSON));

        assertThat(movieTools.getTrendingMovies(LOCAL))
                .isEqualTo("Trending movies this week:\n- Dune: Part Two (2024) · tmdbId 693134 · TMDB 8.2 · no CineCircle reviews yet");
    }

    @Test
    void getMovie_formatsDetail_withLink() {
        setUp(null);
        server.expect(requestTo(API + "/movies/438631"))
                .andRespond(withSuccess("{\"tmdbId\":438631,\"title\":\"Dune\",\"releaseYear\":2021,\"posterUrl\":null,"
                        + "\"tmdbVoteAverage\":7.79,\"reviewCount\":3,\"avgRating\":8.333,\"overview\":\"Paul Atreides "
                        + "travels to Arrakis.\",\"backdropUrl\":null,\"genres\":[\"Science Fiction\",\"Adventure\"]}",
                        MediaType.APPLICATION_JSON));

        assertThat(movieTools.getMovie(438631L, LOCAL)).isEqualTo("""
                Dune (2021) · tmdbId 438631
                Genres: Science Fiction, Adventure
                TMDB rating: 7.8/10 · CineCircle: 3 reviews, avg 8.3/10
                Overview: Paul Atreides travels to Arrakis.
                Link: http://localhost:5173/movies/438631""");
    }

    @Test
    void getMovie_unknownId_mapsMovieNotFound() {
        setUp(null);
        server.expect(requestTo(API + "/movies/999999999"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(404, "MOVIE_NOT_FOUND", "Movie not found")));

        assertThat(toolError(() -> movieTools.getMovie(999999999L, LOCAL)))
                .isEqualTo("CineCircle error MOVIE_NOT_FOUND: Movie not found. "
                        + "Use search_movies to find a valid tmdbId; never guess one.");
    }

    @Test
    void getMovie_invalidId_isRejectedLocally() {
        setUp(null);
        assertThat(toolError(() -> movieTools.getMovie(0L, LOCAL))).contains("VALIDATION_ERROR").contains("search_movies");
        assertThat(toolError(() -> movieTools.getMovie(null, LOCAL))).contains("VALIDATION_ERROR");
    }

    @Test
    void backendDown_retriedOnce_thenWakingUp() {
        setUp(null);
        server.expect(ExpectedCount.times(2), requestTo(API + "/movies/trending"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThat(toolError(() -> movieTools.getTrendingMovies(LOCAL)))
                .isEqualTo(WAKING_UP_UNREACHABLE);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
    }

    @Test
    void nonJsonErrorBody_stillGivesACode() {
        setUp(null);
        server.expect(requestTo(API + "/movies/trending"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR).contentType(MediaType.TEXT_HTML).body("<html>down</html>"));

        assertThat(toolError(() -> movieTools.getTrendingMovies(LOCAL))).startsWith("CineCircle error HTTP_500:");
    }

    @Test
    void readTools_sendBearerToken_whenSet_butNeverEchoIt() {
        setUp(TOKEN);
        server.expect(requestTo(API + "/movies/trending"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess("[" + DUNE + "]", MediaType.APPLICATION_JSON));

        assertThat(movieTools.getTrendingMovies(LOCAL)).doesNotContain(TOKEN).contains("Dune");
    }
}

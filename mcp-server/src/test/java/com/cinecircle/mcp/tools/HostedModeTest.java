package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.auth.RateLimiter;
import com.cinecircle.mcp.config.CineCircleProperties;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** DEPLOY.md "MCP server": per-caller tokens, read-only without a header, env token switch, rate limit, wake-up. */
class HostedModeTest extends ToolTestSupport {

    static final String ENV_TOKEN = "env.operator-token.sig";
    static final String TOKEN_A = "eyJ.alice-token.sigA";
    static final String TOKEN_B = "eyJ.bob-token.sigB";
    static final String HOSTED_REFUSAL = "Add your CineCircle token: see Settings → Use CineCircle with Claude";
    static final String TRENDING = "[{\"tmdbId\":1,\"title\":\"A\",\"releaseYear\":2020,\"reviewCount\":0}]";
    static final String VOTE = "{\"upvoted\":true,\"upvoteCount\":3}";

    /** Hosted configuration: env token present but NOT allowed, no rate limit. */
    void hosted() {
        setUp(new CineCircleProperties(API, ENV_TOKEN, "https://cinecircle.pages.dev", false, 0, null),
                new RateLimiter(0, Clock.systemUTC()));
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class HeaderToken {

        @Test
        void headerTokenIsForwarded_perRequest_eachCallAsItsOwnCaller() {
            hosted();
            server.expect(requestTo(API + "/posts/p1/upvote")).andExpect(method(HttpMethod.PUT))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_A))
                    .andRespond(withSuccess(VOTE, MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/posts/p2/upvote"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_B))
                    .andRespond(withSuccess(VOTE, MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/movies/trending"))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));

            assertThat(reviewTools.upvoteReview("p1", http(TOKEN_A, "1.1.1.1"))).doesNotContain(TOKEN_A);
            assertThat(reviewTools.upvoteReview("p2", http(TOKEN_B, "2.2.2.2"))).doesNotContain(TOKEN_B);
            // the next call without a header must not inherit anything from earlier calls
            assertThat(movieTools.getTrendingMovies(http(null, "1.1.1.1"))).contains("A (2020)");
        }

        @Test
        void headerTokenWinsOverEnvToken_evenWhenEnvTokenAllowed() {
            setUp(TOKEN_B.replace("bob", "env")); // env token allowed (local default)
            server.expect(requestTo(API + "/movies/trending"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_A))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));
            movieTools.getTrendingMovies(http(TOKEN_A, "1.1.1.1"));
        }

        @Test
        void headerTokenNeverAppearsInOutput_evenIfBackendEchoesIt() {
            hosted();
            server.expect(requestTo(API + "/posts/p1/upvote"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(401, "UNAUTHORIZED", "bad token " + TOKEN_A)));

            String err = toolError(() -> reviewTools.upvoteReview("p1", http(TOKEN_A, "1.1.1.1")));
            assertThat(err).startsWith("CineCircle error UNAUTHORIZED").doesNotContain(TOKEN_A).contains("[redacted]");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class NoHeader {

        @Test
        void readToolsWork_readOnly_withoutAnyAuthorization() {
            hosted();
            server.expect(requestTo(API + "/movies/trending"))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));
            assertThat(movieTools.getTrendingMovies(http(null, "1.1.1.1"))).contains("A (2020)");
        }

        @Test
        void writeToolsRefuse_withHostedMessage_andMakeNoRequest() {
            hosted();
            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 8, false, http(null, "1.1.1.1"))))
                    .isEqualTo(HOSTED_REFUSAL);
            assertThat(toolError(() -> reviewTools.upvoteReview("p1", http(null, "1.1.1.1")))).isEqualTo(HOSTED_REFUSAL);
            assertThat(toolError(() -> reviewTools.commentOnReview("p1", "hi", http(null, "1.1.1.1"))))
                    .isEqualTo(HOSTED_REFUSAL);
        }

        @Test
        void localHttp_withEnvTokenAllowed_stillUsesEnvToken() {
            setUp(ENV_TOKEN); // MCP_ALLOW_ENV_TOKEN=true (default): local HTTP use keeps working
            server.expect(requestTo(API + "/posts/p1/upvote"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ENV_TOKEN))
                    .andRespond(withSuccess(VOTE, MediaType.APPLICATION_JSON));
            assertThat(reviewTools.upvoteReview("p1", http(null, "127.0.0.1"))).startsWith("Upvoted.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class EnvTokenSwitch {

        @Test
        void envTokenIgnored_whenNotAllowed_overHttp() {
            hosted();
            server.expect(requestTo(API + "/users/carol"))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(404, "NOT_FOUND", "User not found")));
            toolError(() -> userTools.getUserProfile("carol", http(null, "1.1.1.1")));
            assertThat(toolError(() -> reviewTools.upvoteReview("p1", http(null, "1.1.1.1")))).isEqualTo(HOSTED_REFUSAL);
        }

        @Test
        void envTokenIgnored_whenNotAllowed_overStdio() {
            hosted();
            server.expect(requestTo(API + "/movies/trending"))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));
            movieTools.getTrendingMovies(LOCAL);
            assertThat(toolError(() -> reviewTools.upvoteReview("p1", LOCAL)))
                    .isEqualTo("Set CINECIRCLE_TOKEN to post as your account.");
        }

        @Test
        void envTokenUsed_whenAllowed_overStdio() {
            setUp(ENV_TOKEN);
            server.expect(requestTo(API + "/movies/trending"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + ENV_TOKEN))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));
            assertThat(movieTools.getTrendingMovies(LOCAL)).doesNotContain(ENV_TOKEN);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class RateLimit {

        @Test
        void perIp_limitPerMinute_thenSlowDown_untilNextMinute() {
            AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-09T10:00:10Z"));
            Clock clock = new Clock() {
                @Override
                public ZoneOffset getZone() {
                    return ZoneOffset.UTC;
                }

                @Override
                public Clock withZone(java.time.ZoneId zone) {
                    return this;
                }

                @Override
                public Instant instant() {
                    return now.get();
                }
            };
            setUp(new CineCircleProperties(API, null, null, false, 2, null), new RateLimiter(2, clock));
            server.expect(ExpectedCount.times(4), requestTo(API + "/movies/trending"))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));

            movieTools.getTrendingMovies(http(null, "9.9.9.9"));
            movieTools.getTrendingMovies(http(null, "9.9.9.9"));
            String err = toolError(() -> movieTools.getTrendingMovies(http(null, "9.9.9.9")));
            assertThat(err).isEqualTo("CineCircle error RATE_LIMITED: Slow down — too many requests from your address "
                    + "(limit 2 per minute). Try again in 50 seconds.");
            // another IP has its own budget
            movieTools.getTrendingMovies(http(null, "8.8.8.8"));
            // write tools count too and are refused before anything else
            assertThat(toolError(() -> reviewTools.upvoteReview("p1", http(TOKEN_A, "9.9.9.9")))).contains("Slow down");
            // next minute: allowed again
            now.set(Instant.parse("2026-11-09T10:01:00Z"));
            movieTools.getTrendingMovies(http(null, "9.9.9.9"));
        }

        @Test
        void localCalls_haveNoIp_andAreNotLimited() {
            setUp(new CineCircleProperties(API, null, null, null, 1, null), new RateLimiter(1, Clock.systemUTC()));
            server.expect(ExpectedCount.times(3), requestTo(API + "/movies/trending"))
                    .andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));
            movieTools.getTrendingMovies(LOCAL);
            movieTools.getTrendingMovies(LOCAL);
            movieTools.getTrendingMovies(LOCAL);
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class WakeUpRetry {

        @Test
        void connectError_retriedOnceAfter5s_thenSucceeds() {
            hosted();
            server.expect(requestTo(API + "/movies/trending")).andRespond(withException(new ConnectException("refused")));
            server.expect(requestTo(API + "/movies/trending")).andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));

            assertThat(movieTools.getTrendingMovies(http(null, "1.1.1.1"))).contains("A (2020)");
            assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        }

        @Test
        void gateway502_503_504_retriedOnce() {
            for (HttpStatus status : new HttpStatus[] {HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE,
                    HttpStatus.GATEWAY_TIMEOUT}) {
                hosted();
                server.expect(requestTo(API + "/movies/trending"))
                        .andRespond(withStatus(status).contentType(MediaType.TEXT_HTML).body("<html>waking</html>"));
                server.expect(requestTo(API + "/movies/trending")).andRespond(withSuccess(TRENDING, MediaType.APPLICATION_JSON));

                assertThat(movieTools.getTrendingMovies(http(null, "1.1.1.1"))).contains("A (2020)");
                assertThat(sleeps).hasSize(1);
                server.verify();
            }
        }

        @Test
        void stillFailing_saysWakingUp() {
            hosted();
            server.expect(ExpectedCount.times(2), requestTo(API + "/movies/trending"))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE).body(""));

            assertThat(toolError(() -> movieTools.getTrendingMovies(http(null, "1.1.1.1"))))
                    .startsWith("CineCircle is waking up — try again in a minute.").contains("HTTP 503");
            assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        }

        @Test
        void realBackend502_withCineCircleErrorBody_isNotRetried_andKeepsItsCode() {
            hosted();
            server.expect(ExpectedCount.once(), requestTo(API + "/movies/438631/summary"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(502, "SUMMARY_UNAVAILABLE", "Summary unavailable")));

            assertThat(toolError(() -> reviewTools.getReviewSummary(438631L, http(null, "1.1.1.1"))))
                    .startsWith("CineCircle error SUMMARY_UNAVAILABLE");
            assertThat(sleeps).isEmpty();
        }

        @Test
        void post_gatewayError_isNotRetried_soAReviewIsNeverPostedTwice() {
            hosted();
            server.expect(ExpectedCount.once(), requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.GATEWAY_TIMEOUT).body(""));

            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 8, false, http(TOKEN_A, "1.1.1.1"))))
                    .startsWith("CineCircle is waking up — try again in a minute.");
            assertThat(sleeps).isEmpty();
        }

        @Test
        void post_connectError_isRetried_requestNeverArrived() {
            hosted();
            server.expect(requestTo(API + "/posts/p1/comments")).andRespond(withException(new ConnectException("refused")));
            server.expect(requestTo(API + "/posts/p1/comments"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN_A))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"id\":\"c1\",\"author\":" + user("alice", false) + ",\"body\":\"hi\",\"deleted\":false,"
                                    + "\"createdAt\":\"2026-11-08T10:00:00Z\",\"isMine\":true,\"replies\":[]}"));

            assertThat(reviewTools.commentOnReview("p1", "hi", http(TOKEN_A, "1.1.1.1")))
                    .isEqualTo("Comment posted (id c1).\nLink: https://cinecircle.pages.dev/posts/p1");
            assertThat(sleeps).containsExactly(Duration.ofSeconds(5));
        }

        @Test
        void readTimeout_isNotRetried() {
            hosted();
            server.expect(ExpectedCount.once(), requestTo(API + "/movies/trending"))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));

            assertThat(toolError(() -> movieTools.getTrendingMovies(http(null, "1.1.1.1"))))
                    .isEqualTo("CineCircle API is not reachable at http://localhost:8080/api");
            assertThat(sleeps).isEmpty();
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class Links {

        @Test
        void reviewLinksUseCineCircleWebUrl() {
            setUp(new CineCircleProperties(API, null, "https://cinecircle.pages.dev/", false, 0, null),
                    new RateLimiter(0, Clock.systemUTC()));
            server.expect(requestTo(API + "/movies/438631/posts?sort=top&page=0&size=10"))
                    .andRespond(withSuccess(page(true, post("p1", "t", "b", 8, false, noSpoiler())), MediaType.APPLICATION_JSON));

            String out = reviewTools.getMovieReviews(438631L, null, null, null, http(null, "1.1.1.1"));
            assertThat(out).contains("https://cinecircle.pages.dev/posts/p1")
                    .contains("https://cinecircle.pages.dev/movies/438631")
                    .doesNotContain("localhost:5173");
        }
    }
}

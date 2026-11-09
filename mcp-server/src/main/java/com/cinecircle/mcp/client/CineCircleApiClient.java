package com.cinecircle.mcp.client;

import com.cinecircle.mcp.auth.Caller;
import com.cinecircle.mcp.client.dto.ApiError;
import com.cinecircle.mcp.client.dto.Comment;
import com.cinecircle.mcp.client.dto.CreateCommentRequest;
import com.cinecircle.mcp.client.dto.CreatePostRequest;
import com.cinecircle.mcp.client.dto.MovieDetail;
import com.cinecircle.mcp.client.dto.MovieSummary;
import com.cinecircle.mcp.client.dto.Page;
import com.cinecircle.mcp.client.dto.PostDetail;
import com.cinecircle.mcp.client.dto.PostSummary;
import com.cinecircle.mcp.client.dto.ReviewSummary;
import com.cinecircle.mcp.client.dto.UserProfile;
import com.cinecircle.mcp.client.dto.VoteResponse;
import com.cinecircle.mcp.config.CineCircleProperties;
import com.cinecircle.mcp.error.ApiUnreachableException;
import com.cinecircle.mcp.error.BackendWakingUpException;
import com.cinecircle.mcp.error.CineCircleApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.http.HttpConnectTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Typed client for the CineCircle REST API. This is the ONLY way the MCP server talks to CineCircle:
 * no MongoDB, no TMDB, no LLM calls. Visibility, ownership and movie validation stay in the backend.
 *
 * <p>Every method takes the {@link Caller} of the current tool call; its token (if any) is sent as
 * {@code Authorization: Bearer} on that request only. The client itself holds no per-caller state.
 * Tokens are never logged and never part of an exception message.
 *
 * <p>Free hosting sleeps when idle: on a connect error or a gateway 502/503/504 (one without the CineCircle error
 * body) the request is retried once after {@code cinecircle.retry-delay} (5 s). Non-idempotent POSTs are only
 * retried on connect errors (the request never reached the server), so a review or comment is never posted twice.
 * If it still fails → {@link BackendWakingUpException}. The read timeout (70 s) is set in application.yml.
 */
@Component
public class CineCircleApiClient {

    private static final Logger log = LoggerFactory.getLogger(CineCircleApiClient.class);

    private static final ParameterizedTypeReference<Page<MovieSummary>> MOVIE_PAGE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<MovieSummary>> MOVIE_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Page<PostSummary>> POST_PAGE = new ParameterizedTypeReference<>() {
    };
    private static final Set<Integer> GATEWAY_STATUSES = Set.of(502, 503, 504);

    private final RestClient rest;
    private final CineCircleProperties props;
    private final RetrySleeper sleeper;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CineCircleApiClient(RestClient.Builder builder, CineCircleProperties props, RetrySleeper sleeper) {
        this.props = props;
        this.sleeper = sleeper;
        this.rest = builder
                .baseUrl(props.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toApiException(response);
                })
                .build();
    }

    public String apiUrl() {
        return props.apiUrl();
    }

    // ---- movies ----------------------------------------------------------------------------------------------

    /** {@code GET /movies/search?q=} (first page). */
    public Page<MovieSummary> searchMovies(Caller caller, String query) {
        return idempotent(() -> rest.get().uri("/movies/search?q={q}&page=0", query)
                .headers(caller::applyTo).retrieve().body(MOVIE_PAGE));
    }

    /** {@code GET /movies/trending}. */
    public List<MovieSummary> trendingMovies(Caller caller) {
        return idempotent(() -> rest.get().uri("/movies/trending")
                .headers(caller::applyTo).retrieve().body(MOVIE_LIST));
    }

    /** {@code GET /movies/{tmdbId}}. */
    public MovieDetail movie(Caller caller, long tmdbId) {
        return idempotent(() -> rest.get().uri("/movies/{id}", tmdbId)
                .headers(caller::applyTo).retrieve().body(MovieDetail.class));
    }

    /** {@code GET /movies/{tmdbId}/posts?sort=&page=0&size=}. */
    public Page<PostSummary> moviePosts(Caller caller, long tmdbId, String sort, int size) {
        return idempotent(() -> rest.get().uri("/movies/{id}/posts?sort={sort}&page=0&size={size}", tmdbId, sort, size)
                .headers(caller::applyTo).retrieve().body(POST_PAGE));
    }

    /** {@code GET /movies/{tmdbId}/summary}. */
    public ReviewSummary reviewSummary(Caller caller, long tmdbId) {
        return idempotent(() -> rest.get().uri("/movies/{id}/summary", tmdbId)
                .headers(caller::applyTo).retrieve().body(ReviewSummary.class));
    }

    // ---- posts -----------------------------------------------------------------------------------------------

    /** {@code GET /posts/popular?window=&page=0&size=}. */
    public Page<PostSummary> popularPosts(Caller caller, String window, int size) {
        return idempotent(() -> rest.get().uri("/posts/popular?window={w}&page=0&size={size}", window, size)
                .headers(caller::applyTo).retrieve().body(POST_PAGE));
    }

    /** {@code POST /posts} multipart with JSON part {@code post}. */
    public PostDetail createPost(Caller caller, CreatePostRequest request) {
        String postJson;
        try {
            postJson = json.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the review", e);
        }
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("post", postJson, MediaType.APPLICATION_JSON);
        return nonIdempotent(() -> rest.post().uri("/posts")
                .headers(caller::applyTo)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts.build())
                .retrieve().body(PostDetail.class));
    }

    /** {@code PUT /posts/{id}/upvote} (idempotent on the backend). */
    public VoteResponse upvote(Caller caller, String postId) {
        return idempotent(() -> rest.put().uri("/posts/{id}/upvote", postId)
                .headers(caller::applyTo).retrieve().body(VoteResponse.class));
    }

    /** {@code POST /posts/{id}/comments}. */
    public Comment comment(Caller caller, String postId, String body) {
        return nonIdempotent(() -> rest.post().uri("/posts/{id}/comments", postId)
                .headers(caller::applyTo)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCommentRequest(body, null))
                .retrieve().body(Comment.class));
    }

    // ---- users -----------------------------------------------------------------------------------------------

    /** {@code GET /users/{username}}. */
    public UserProfile user(Caller caller, String username) {
        return idempotent(() -> rest.get().uri("/users/{username}", username)
                .headers(caller::applyTo).retrieve().body(UserProfile.class));
    }

    // ---- retry / error plumbing ------------------------------------------------------------------------------

    private <T> T idempotent(Supplier<T> request) {
        return call(request, true);
    }

    private <T> T nonIdempotent(Supplier<T> request) {
        return call(request, false);
    }

    private <T> T call(Supplier<T> request, boolean idempotent) {
        try {
            return request.get();
        } catch (RuntimeException first) {
            String reason = sleepyReason(first);
            if (reason == null) {
                throw translate(first);
            }
            boolean connectError = first instanceof ResourceAccessException;
            if (!idempotent && !connectError) {
                // A gateway error on a POST may mean the backend did process it: never post twice.
                throw new BackendWakingUpException(props.apiUrl(), reason, first);
            }
            log.info("CineCircle API looks asleep ({}); retrying once in {}", reason, props.retryDelay());
            pause(props.retryDelay(), first);
            try {
                return request.get();
            } catch (RuntimeException second) {
                String again = sleepyReason(second);
                if (again != null) {
                    throw new BackendWakingUpException(props.apiUrl(), again, second);
                }
                throw translate(second);
            }
        }
    }

    private void pause(Duration delay, RuntimeException cause) {
        try {
            sleeper.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BackendWakingUpException(props.apiUrl(), "interrupted while waiting", cause);
        }
    }

    /** Non-null (a short, token-free reason) when the failure looks like a sleeping/starting backend. */
    private static String sleepyReason(RuntimeException e) {
        if (e instanceof ResourceAccessException rae && isConnectError(rae)) {
            return "not reachable";
        }
        if (e instanceof CineCircleApiException api && isGatewayError(api)) {
            return "HTTP " + api.status();
        }
        return null;
    }

    /** Only proxy/gateway answers count; a real backend 502 (e.g. TMDB_UNAVAILABLE) keeps its own code. */
    private static boolean isGatewayError(CineCircleApiException e) {
        return GATEWAY_STATUSES.contains(e.status()) && e.code() != null && e.code().startsWith("HTTP_");
    }

    private static boolean isConnectError(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException
                    || t instanceof NoRouteToHostException || t instanceof HttpConnectTimeoutException) {
                return true;
            }
            if (t instanceof SocketTimeoutException && t.getMessage() != null
                    && t.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return false;
    }

    private RuntimeException translate(RuntimeException e) {
        if (e instanceof ResourceAccessException) {
            return new ApiUnreachableException(props.apiUrl(), e);
        }
        return e;
    }

    private CineCircleApiException toApiException(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        String raw;
        try {
            raw = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            raw = "";
        }
        try {
            ApiError err = json.readValue(raw, ApiError.class);
            if (err != null && err.code() != null && !err.code().isBlank()) {
                return new CineCircleApiException(status, err.code(), err.message(), err.fieldErrors());
            }
        } catch (IOException ignored) {
            // not the CineCircle error shape (proxy page, empty body ...)
        }
        return new CineCircleApiException(status, "HTTP_" + status,
                "Unexpected response from the CineCircle API (HTTP " + status + ")", null);
    }
}

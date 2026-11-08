package com.cinecircle.mcp.client;

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
import com.cinecircle.mcp.error.CineCircleApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

/**
 * Typed client for the CineCircle REST API. This is the ONLY way the MCP server talks to CineCircle:
 * no MongoDB, no TMDB, no LLM calls. Visibility, ownership and movie validation stay in the backend.
 *
 * <p>When {@code CINECIRCLE_TOKEN} is set it is sent as {@code Authorization: Bearer} on every request.
 * The token is never logged and never part of an exception message.
 */
@Component
public class CineCircleApiClient {

    private static final ParameterizedTypeReference<Page<MovieSummary>> MOVIE_PAGE = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<List<MovieSummary>> MOVIE_LIST = new ParameterizedTypeReference<>() {
    };
    private static final ParameterizedTypeReference<Page<PostSummary>> POST_PAGE = new ParameterizedTypeReference<>() {
    };

    private final RestClient rest;
    private final CineCircleProperties props;
    private final ObjectMapper json = new ObjectMapper()
            .findAndRegisterModules()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CineCircleApiClient(RestClient.Builder builder, CineCircleProperties props) {
        this.props = props;
        this.rest = builder
                .baseUrl(props.apiUrl())
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .requestInterceptor((request, body, execution) -> {
                    if (props.hasToken()) {
                        request.getHeaders().setBearerAuth(props.token());
                    }
                    return execution.execute(request, body);
                })
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw toApiException(response);
                })
                .build();
    }

    public boolean hasToken() {
        return props.hasToken();
    }

    public String apiUrl() {
        return props.apiUrl();
    }

    // ---- movies ----------------------------------------------------------------------------------------------

    /** {@code GET /movies/search?q=} (first page). */
    public Page<MovieSummary> searchMovies(String query) {
        return call(() -> rest.get().uri("/movies/search?q={q}&page=0", query).retrieve().body(MOVIE_PAGE));
    }

    /** {@code GET /movies/trending}. */
    public List<MovieSummary> trendingMovies() {
        return call(() -> rest.get().uri("/movies/trending").retrieve().body(MOVIE_LIST));
    }

    /** {@code GET /movies/{tmdbId}}. */
    public MovieDetail movie(long tmdbId) {
        return call(() -> rest.get().uri("/movies/{id}", tmdbId).retrieve().body(MovieDetail.class));
    }

    /** {@code GET /movies/{tmdbId}/posts?sort=&page=0&size=}. */
    public Page<PostSummary> moviePosts(long tmdbId, String sort, int size) {
        return call(() -> rest.get().uri("/movies/{id}/posts?sort={sort}&page=0&size={size}", tmdbId, sort, size)
                .retrieve().body(POST_PAGE));
    }

    /** {@code GET /movies/{tmdbId}/summary}. */
    public ReviewSummary reviewSummary(long tmdbId) {
        return call(() -> rest.get().uri("/movies/{id}/summary", tmdbId).retrieve().body(ReviewSummary.class));
    }

    // ---- posts -----------------------------------------------------------------------------------------------

    /** {@code GET /posts/popular?window=&page=0&size=}. */
    public Page<PostSummary> popularPosts(String window, int size) {
        return call(() -> rest.get().uri("/posts/popular?window={w}&page=0&size={size}", window, size)
                .retrieve().body(POST_PAGE));
    }

    /** {@code POST /posts} multipart with JSON part {@code post}. */
    public PostDetail createPost(CreatePostRequest request) {
        String postJson;
        try {
            postJson = json.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise the review", e);
        }
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("post", postJson, MediaType.APPLICATION_JSON);
        return call(() -> rest.post().uri("/posts")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts.build())
                .retrieve().body(PostDetail.class));
    }

    /** {@code PUT /posts/{id}/upvote}. */
    public VoteResponse upvote(String postId) {
        return call(() -> rest.put().uri("/posts/{id}/upvote", postId).retrieve().body(VoteResponse.class));
    }

    /** {@code POST /posts/{id}/comments}. */
    public Comment comment(String postId, String body) {
        return call(() -> rest.post().uri("/posts/{id}/comments", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new CreateCommentRequest(body, null))
                .retrieve().body(Comment.class));
    }

    // ---- users -----------------------------------------------------------------------------------------------

    /** {@code GET /users/{username}}. */
    public UserProfile user(String username) {
        return call(() -> rest.get().uri("/users/{username}", username).retrieve().body(UserProfile.class));
    }

    // ---- plumbing --------------------------------------------------------------------------------------------

    private <T> T call(Supplier<T> request) {
        try {
            return request.get();
        } catch (ResourceAccessException e) {
            throw new ApiUnreachableException(props.apiUrl(), e);
        }
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

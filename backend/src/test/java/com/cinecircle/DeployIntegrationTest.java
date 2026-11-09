package com.cinecircle;

import com.cinecircle.comment.Comment;
import com.cinecircle.common.RateLimiter;
import com.cinecircle.follow.Follow;
import com.cinecircle.llm.LlmClient;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.tmdb.TmdbClient;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.post.Post;
import com.cinecircle.user.User;
import com.cinecircle.vote.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * DEPLOY.md backend checks with production-like settings: low rate limits, 1 MB images, a tiny storage limit, a
 * non-localhost public base URL and a comma-separated CORS origin list (bound from one string, as from the env var).
 */
@SpringBootTest(properties = {
        "app.public-base-url=https://api.cinecircle.test/",
        "app.cors.origins=https://cinecircle.pages.dev, http://localhost:5173",
        "app.images.max-bytes=1048576",
        "app.images.storage-limit-mb=1",
        "app.limits.posts-per-day=2",
        "app.limits.comments-per-day=2",
        "app.limits.register-per-hour-per-ip=3",
        "app.limits.login-per-minute-per-ip=2"
})
@AutoConfigureMockMvc
class DeployIntegrationTest {

    private static final long MATRIX = 603L;
    private static final long UNKNOWN = 999_999_999L;
    private static final byte[] PNG_HEADER = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MongoTemplate mongo;
    @Autowired RateLimiter rateLimiter;

    @MockitoBean TmdbClient tmdb;
    @MockitoBean LlmClient llm; // disabled: spoiler status SKIPPED, no AI calls

    private int ipCounter;

    @BeforeEach
    void reset() {
        for (Class<?> c : List.of(User.class, Follow.class, Post.class, Vote.class, Comment.class, Movie.class)) {
            mongo.remove(new Query(), c);
        }
        mongo.getCollection("images.files").deleteMany(new Document());
        mongo.getCollection("images.chunks").deleteMany(new Document());
        rateLimiter.reset();
        when(llm.isEnabled()).thenReturn(false);
        when(tmdb.getMovie(anyLong())).thenReturn(Optional.empty());
        when(tmdb.getMovie(MATRIX)).thenReturn(Optional.of(new TmdbModels.MovieDetails(MATRIX, "The Matrix",
                "The Matrix", "1999-03-30", "Neo wakes up.", "/matrix.jpg", "/matrix-bg.jpg", 8.2, List.of())));
    }

    // ------------------------------------------------------------------ health

    @Test
    void healthIsPublicAndCheap() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"ok\"}", true));
        // a broken token must not turn it into a 401
        mvc.perform(get("/api/health").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    // ------------------------------------------------------------------ CORS

    @Test
    void corsAllowsConfiguredOriginsAndRejectsOthers() throws Exception {
        for (String origin : List.of("https://cinecircle.pages.dev", "http://localhost:5173")) {
            preflight(origin)
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
            mvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, origin))
                    .andExpect(status().isOk())
                    .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin));
        }
        preflight("https://evil.example")
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        mvc.perform(get("/api/health").header(HttpHeaders.ORIGIN, "https://evil.example"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private ResultActions preflight(String origin) throws Exception {
        return mvc.perform(options("/api/posts")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"));
    }

    // ------------------------------------------------------------------ public base URL

    @Test
    void imageUrlsUsePublicBaseUrl() throws Exception {
        String token = register("neo");
        String res = mvc.perform(avatar(token, png(1024)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(startsWith("https://api.cinecircle.test/api/images/")))
                .andReturn().getResponse().getContentAsString();
        String url = json.readTree(res).get("avatarUrl").asText();
        assertThat(url).doesNotContain("localhost").doesNotContain(".test//");

        String postRes = mvc.perform(multipart("/api/posts").file(postPart("CUSTOM"))
                        .file(new MockMultipartFile("image", "x.png", "image/png", png(512)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(startsWith("https://api.cinecircle.test/api/images/")))
                .andExpect(jsonPath("$.author.avatarUrl").value(url))
                .andReturn().getResponse().getContentAsString();
        // the image itself is still served under /api/images/{id}
        String imagePath = json.readTree(postRes).get("imageUrl").asText().substring("https://api.cinecircle.test".length());
        mvc.perform(get(imagePath)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ storage guard

    @Test
    void imageOverMaxBytesIs400WithConfiguredLimit() throws Exception {
        String token = register("neo");
        mvc.perform(avatar(token, png(1024 * 1024 + 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Image must be under 1 MB"))
                .andExpect(jsonPath("$.fieldErrors.file").value("Image must be under 1 MB"));

        mvc.perform(multipart("/api/posts").file(postPart("CUSTOM"))
                        .file(new MockMultipartFile("image", "x.png", "image/png", png(2 * 1024 * 1024)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.image").value("Image must be under 1 MB"));
        assertThat(mongo.count(new Query(), Post.class)).isZero();

        // exactly at the limit is fine
        mvc.perform(avatar(token, png(1024 * 1024))).andExpect(status().isOk());
    }

    @Test
    void fullStorageIs507() throws Exception {
        String token = register("neo");
        // pretend 1 MB of images is already stored (limit is 1 MB)
        mongo.getCollection("images.files").insertOne(new Document("length", 1024L * 1024L)
                .append("chunkSize", 261120).append("uploadDate", new Date()).append("filename", "big.png"));

        mvc.perform(avatar(token, png(100)))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.status").value(507))
                .andExpect(jsonPath("$.code").value("STORAGE_FULL"))
                .andExpect(jsonPath("$.message").isString());
        mvc.perform(multipart("/api/posts").file(postPart("CUSTOM"))
                        .file(new MockMultipartFile("image", "x.png", "image/png", png(100)))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isInsufficientStorage())
                .andExpect(jsonPath("$.code").value("STORAGE_FULL"));
        // posts without a custom image still work
        mvc.perform(multipart("/api/posts").file(postPart("NONE"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ rate limits

    @Test
    void postsPerDayLimitIs429AndRejectedPostsDoNotCount() throws Exception {
        String token = register("neo");
        // an invalid tmdbId is rejected and gives its slot back
        mvc.perform(multipart("/api/posts").file(postPart("NONE", UNKNOWN))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest());
        for (int i = 0; i < 2; i++) {
            mvc.perform(multipart("/api/posts").file(postPart("NONE"))
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                    .andExpect(status().isCreated());
        }
        mvc.perform(multipart("/api/posts").file(postPart("NONE"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("You've posted 2 reviews today — come back tomorrow!"));
        assertThat(mongo.count(new Query(), Post.class)).isEqualTo(2);

        // per user: someone else can still post
        String other = register("trinity");
        mvc.perform(multipart("/api/posts").file(postPart("NONE"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isCreated());
    }

    @Test
    void commentsPerDayLimitIs429() throws Exception {
        String token = register("neo");
        String postId = json.readTree(mvc.perform(multipart("/api/posts").file(postPart("NONE"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();
        for (int i = 0; i < 2; i++) {
            comment(token, postId).andExpect(status().isCreated());
        }
        comment(token, postId)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("You've posted 2 comments today — come back tomorrow!"));
        assertThat(mongo.count(new Query(), Comment.class)).isEqualTo(2);
    }

    @Test
    void registerPerHourPerIpIs429AndUsesFirstForwardedForEntry() throws Exception {
        String ip = "203.0.113.7";
        for (int i = 0; i < 3; i++) {
            registerFrom("user" + i, ip + ", 10.0.0.1").andExpect(status().isCreated());
        }
        registerFrom("user9", ip + ", 10.0.0.2")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").isString());
        assertThat(mongo.count(new Query(), User.class)).isEqualTo(3);
        // a different client IP (first X-Forwarded-For entry) is not affected
        registerFrom("user10", "198.51.100.1, 10.0.0.1").andExpect(status().isCreated());
    }

    @Test
    void failedRegistrationsDoNotUseTheAllowance() throws Exception {
        String ip = "203.0.113.8";
        registerFrom("taken", ip).andExpect(status().isCreated());
        for (int i = 0; i < 5; i++) {
            registerFrom("taken", ip).andExpect(status().isConflict());
        }
        registerFrom("second", ip).andExpect(status().isCreated());
    }

    @Test
    void registerWithoutForwardedForUsesRemoteAddress() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(registerRequest("remote" + i).with(r -> {
                r.setRemoteAddr("192.0.2.50");
                return r;
            })).andExpect(status().isCreated());
        }
        mvc.perform(registerRequest("remote9").with(r -> {
            r.setRemoteAddr("192.0.2.50");
            return r;
        })).andExpect(status().isTooManyRequests());
    }

    @Test
    void loginPerMinutePerIpIs429() throws Exception {
        register("neo");
        String ip = "203.0.113.9";
        login("neo", "wrongpass", ip).andExpect(status().isUnauthorized());
        login("neo", "password123", ip).andExpect(status().isOk());
        login("neo", "password123", ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").isString());
        login("neo", "password123", "198.51.100.9").andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ helpers

    private String register(String username) throws Exception {
        String res = registerFrom(username, "10.9.0." + (++ipCounter))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private ResultActions registerFrom(String username, String forwardedFor) throws Exception {
        return mvc.perform(registerRequest(username).header("X-Forwarded-For", forwardedFor));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder registerRequest(String username)
            throws Exception {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "email", username + "@test.dev",
                        "password", "password123", "displayName", username)));
    }

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", ip)
                .content(json.writeValueAsString(Map.of("usernameOrEmail", username, "password", password))));
    }

    private ResultActions comment(String token, String postId) throws Exception {
        return mvc.perform(post("/api/posts/" + postId + "/comments").contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .content("{\"body\":\"Nice\"}"));
    }

    private MockMultipartFile postPart(String imageType) throws Exception {
        return postPart(imageType, MATRIX);
    }

    private MockMultipartFile postPart(String imageType, long tmdbId) throws Exception {
        Map<String, Object> body = Map.of("tmdbId", tmdbId, "title", "Whoa", "body", "Great movie.", "rating", 9,
                "containsSpoilers", false, "imageType", imageType);
        return new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(body));
    }

    private org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder avatar(
            String token, byte[] bytes) {
        var b = multipart(HttpMethod.PUT, "/api/users/me/avatar");
        b.file(new MockMultipartFile("file", "a.png", "image/png", bytes));
        b.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return b;
    }

    /** A PNG-signature file of exactly {@code size} bytes. */
    private static byte[] png(int size) {
        byte[] b = new byte[size];
        System.arraycopy(PNG_HEADER, 0, b, 0, Math.min(PNG_HEADER.length, size));
        return b;
    }

}

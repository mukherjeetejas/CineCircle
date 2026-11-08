package com.cinecircle;

import com.cinecircle.comment.Comment;
import com.cinecircle.follow.Follow;
import com.cinecircle.llm.LlmClient;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.tmdb.TmdbClient;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.post.HotScore;
import com.cinecircle.post.ImageType;
import com.cinecircle.post.Post;
import com.cinecircle.spoiler.AiSpoilerStatus;
import com.cinecircle.summary.MovieReviewSummary;
import com.cinecircle.user.User;
import com.cinecircle.vote.Vote;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Shared Spring context for the v2 AI integration tests (embedded MongoDB, TMDB and the LLM client mocked -
 * no real API calls). Subclasses share one cached context because the bean overrides are identical.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AiIntegrationSupport {

    protected static final long MATRIX = 603L;
    protected static final long UNKNOWN = 999_999_999L;

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected MongoTemplate mongo;

    @MockitoBean protected TmdbClient tmdb;
    @MockitoBean protected LlmClient llm;

    @BeforeEach
    void resetDbAndStubs() {
        for (Class<?> c : List.of(User.class, Follow.class, Post.class, Vote.class, Comment.class, Movie.class,
                MovieReviewSummary.class)) {
            mongo.remove(new Query(), c);
        }
        when(tmdb.getMovie(anyLong())).thenReturn(Optional.empty());
        when(tmdb.getMovie(MATRIX)).thenReturn(Optional.of(new TmdbModels.MovieDetails(MATRIX, "The Matrix",
                "The Matrix", "1999-03-30", "A hacker learns the nature of his reality.", "/matrix.jpg",
                "/matrix-bg.jpg", 8.2, List.of(new TmdbModels.Genre(878L, "Science Fiction")))));
        when(llm.isEnabled()).thenReturn(true);
        when(llm.model()).thenReturn("test-model");
    }

    protected String register(String username) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "email", username + "@test.dev",
                                "password", "password123", "displayName", username))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    protected ResultActions createPost(String token, String title, String body, boolean authorSpoiler)
            throws Exception {
        Map<String, Object> post = Map.of("tmdbId", MATRIX, "title", title, "body", body, "rating", 8,
                "containsSpoilers", authorSpoiler, "imageType", "NONE");
        return mvc.perform(multipart("/api/posts")
                .file(new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(post)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    /** Inserts a post directly (status null = pre-v2 document without the field). */
    protected Post insertPost(String author, boolean authorPrivate, boolean authorSpoiler, AiSpoilerStatus status,
                              int rating, String title, String body, Instant createdAt) {
        Post p = Post.builder()
                .authorId("id-" + author).authorUsername(author).authorIsPrivate(authorPrivate)
                .tmdbId(MATRIX).movieTitle("The Matrix").movieYear(1999).moviePosterPath("/matrix.jpg")
                .title(title).body(body).rating(rating).containsSpoilers(authorSpoiler)
                .imageType(ImageType.NONE).hotScore(HotScore.compute(0, createdAt))
                .createdAt(createdAt).updatedAt(createdAt)
                .aiSpoilerStatus(status)
                .build();
        return mongo.insert(p);
    }

    protected Post insertPublicClean(String author, int rating, String title) {
        return insertPost(author, false, false, AiSpoilerStatus.CLEAN, rating, title, "Body of " + title,
                Instant.now().minus(Duration.ofMinutes(5)));
    }
}

package com.cinecircle;

import com.cinecircle.comment.Comment;
import com.cinecircle.follow.Follow;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.tmdb.TmdbClient;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.movie.tmdb.TmdbUnavailableException;
import com.cinecircle.post.Post;
import com.cinecircle.user.User;
import com.cinecircle.vote.Vote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    private static final long MATRIX = 603L;
    private static final long UNKNOWN = 999_999_999L;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired MongoTemplate mongo;

    @MockitoBean TmdbClient tmdb;

    @BeforeEach
    void cleanDbAndStubTmdb() {
        // remove documents but keep the (unique) indexes
        for (Class<?> c : List.of(User.class, Follow.class, Post.class, Vote.class, Comment.class, Movie.class)) {
            mongo.remove(new Query(), c);
        }
        mongo.getCollection("images.files").deleteMany(new org.bson.Document());
        mongo.getCollection("images.chunks").deleteMany(new org.bson.Document());

        when(tmdb.getMovie(anyLong())).thenReturn(Optional.empty());
        when(tmdb.getMovie(MATRIX)).thenReturn(Optional.of(new TmdbModels.MovieDetails(MATRIX, "The Matrix",
                "The Matrix", "1999-03-30", "Neo wakes up.", "/matrix.jpg", "/matrix-bg.jpg", 8.2,
                List.of(new TmdbModels.Genre(28L, "Action"), new TmdbModels.Genre(878L, "Science Fiction")))));
    }

    // ------------------------------------------------------------------ auth

    @Test
    void registerThenLoginWithUsernameAndEmail() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "Neo_1", "email", "Neo@Example.com",
                                "password", "password123", "displayName", "Neo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.user.username").value("neo_1"))
                .andExpect(jsonPath("$.user.isPrivate").value(false))
                .andExpect(jsonPath("$.user.canViewPosts").value(true))
                .andExpect(jsonPath("$.user.followStatus").value("SELF"))
                .andExpect(jsonPath("$.user.createdAt").value(
                        org.hamcrest.Matchers.matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")))
                .andExpect(jsonPath("$.user").value(not(hasKey("private"))))
                .andExpect(jsonPath("$.user").value(not(hasKey("passwordHash"))));

        String token = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("usernameOrEmail", "neo_1", "password", "password123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String jwt = json.readTree(token).get("token").asText();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("usernameOrEmail", "neo@example.com", "password", "password123"))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("neo_1"));
    }

    @Test
    void authErrors() throws Exception {
        register("trinity");
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("usernameOrEmail", "trinity", "password", "wrongpass"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "trinity", "email", "other@x.com",
                                "password", "password123", "displayName", "T"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USERNAME_TAKEN"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "other", "email", "trinity@test.dev",
                                "password", "password123", "displayName", "T"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "x!", "email", "bad", "password", "short",
                                "displayName", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());

        mvc.perform(get("/api/feed"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    // ------------------------------------------------------------------ posts

    @Test
    void createPostWithValidTmdbId() throws Exception {
        String token = register("neo");
        createPostRequest(token, MATRIX, "TMDB_POSTER")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.movie.tmdbId").value(MATRIX))
                .andExpect(jsonPath("$.movie.title").value("The Matrix"))
                .andExpect(jsonPath("$.movie.releaseYear").value(1999))
                .andExpect(jsonPath("$.movie.posterUrl").value("https://image.tmdb.org/t/p/w500/matrix.jpg"))
                .andExpect(jsonPath("$.imageUrl").value("https://image.tmdb.org/t/p/w500/matrix.jpg"))
                .andExpect(jsonPath("$.body").value("Whoa. Great movie."))
                .andExpect(jsonPath("$.bodyPreview").value("Whoa. Great movie."))
                .andExpect(jsonPath("$.isMine").value(true))
                .andExpect(jsonPath("$.upvotedByMe").value(false))
                .andExpect(jsonPath("$.containsSpoilers").value(false))
                .andExpect(jsonPath("$.author.username").value("neo"))
                .andExpect(jsonPath("$.author.isPrivate").value(false))
                .andExpect(jsonPath("$").value(not(hasKey("mine"))))
                .andExpect(jsonPath("$").value(not(hasKey("source"))));

        mvc.perform(get("/api/users/neo")).andExpect(jsonPath("$.postCount").value(1));
        mvc.perform(get("/api/movies/" + MATRIX))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.avgRating").value(9.0))
                .andExpect(jsonPath("$.genres[1]").value("Science Fiction"))
                .andExpect(jsonPath("$.backdropUrl").isString());
    }

    @Test
    void createPostWithUnknownTmdbIdIsRejected() throws Exception {
        String token = register("neo");
        createPostRequest(token, UNKNOWN, "NONE")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
        assertThat(mongo.count(new Query(), Post.class)).isZero();

        mvc.perform(get("/api/movies/" + UNKNOWN))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }

    @Test
    void createPostWhenTmdbIsDownGives502() throws Exception {
        when(tmdb.getMovie(42L)).thenThrow(new TmdbUnavailableException("down"));
        String token = register("neo");
        createPostRequest(token, 42L, "NONE")
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("TMDB_UNAVAILABLE"));
    }

    @Test
    void createPostValidationAndCustomImage() throws Exception {
        String token = register("neo");
        // CUSTOM without image
        createPostRequest(token, MATRIX, "CUSTOM")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.image").exists());
        // rating out of range
        mvc.perform(multipart("/api/posts").file(postPart(Map.of("tmdbId", MATRIX, "title", "t", "body", "b",
                                "rating", 11, "imageType", "NONE")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").exists());
        // wrong file type
        mvc.perform(multipart("/api/posts").file(postPart(postBody(MATRIX, "CUSTOM")))
                        .file(new MockMultipartFile("image", "x.gif", "image/gif", new byte[]{'G', 'I', 'F', '8'}))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.image").exists());

        // valid PNG
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        String res = mvc.perform(multipart("/api/posts").file(postPart(postBody(MATRIX, "CUSTOM")))
                        .file(new MockMultipartFile("image", "x.png", "image/png", png))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.imageUrl").value(org.hamcrest.Matchers.startsWith("http://localhost:8080/api/images/")))
                .andReturn().getResponse().getContentAsString();
        String imageUrl = json.readTree(res).get("imageUrl").asText();
        String imagePath = imageUrl.substring("http://localhost:8080".length());
        mvc.perform(get(imagePath))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/png"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=86400"));

        // deleting the post removes the GridFS image
        String postId = json.readTree(res).get("id").asText();
        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get(imagePath)).andExpect(status().isNotFound());
    }

    @Test
    void upvoteIsIdempotent() throws Exception {
        String author = register("neo");
        String voter = register("morpheus");
        String postId = createPost(author);

        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/posts/" + postId + "/upvote").header(HttpHeaders.AUTHORIZATION, "Bearer " + voter))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upvoted").value(true))
                    .andExpect(jsonPath("$.upvoteCount").value(1));
        }
        assertThat(mongo.count(new Query(), Vote.class)).isEqualTo(1);
        Post stored = mongo.findById(postId, Post.class);
        assertThat(stored.getHotScore()).isEqualTo(
                com.cinecircle.post.HotScore.compute(1, stored.getCreatedAt()));

        mvc.perform(get("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, "Bearer " + voter))
                .andExpect(jsonPath("$.upvotedByMe").value(true))
                .andExpect(jsonPath("$.isMine").value(false));
        mvc.perform(get("/api/posts/" + postId))
                .andExpect(jsonPath("$.upvotedByMe").value(false));

        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/posts/" + postId + "/upvote").header(HttpHeaders.AUTHORIZATION, "Bearer " + voter))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.upvoted").value(false))
                    .andExpect(jsonPath("$.upvoteCount").value(0));
        }
    }

    @Test
    void deleteByNonOwnerIsForbidden() throws Exception {
        String author = register("neo");
        String other = register("smith");
        String postId = createPost(author);

        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, "Bearer " + other))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));

        mvc.perform(delete("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, "Bearer " + author))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/posts/" + postId)).andExpect(status().isNotFound());
        mvc.perform(get("/api/users/neo")).andExpect(jsonPath("$.postCount").value(0));
    }

    // ------------------------------------------------------------------ privacy & follows

    @Test
    void privateProfilePostsRequireAcceptedFollow() throws Exception {
        String carol = register("carol");
        String dave = register("dave");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true));
        String postId = createPost(carol);

        // anonymous and stranger: 403 PRIVATE_ACCOUNT, profile header still visible
        mvc.perform(get("/api/users/carol/posts"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        mvc.perform(get("/api/users/carol/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        mvc.perform(get("/api/users/carol").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.canViewPosts").value(false))
                .andExpect(jsonPath("$.followStatus").value("NONE"))
                .andExpect(jsonPath("$.postCount").value(1));
        mvc.perform(get("/api/posts/" + postId).header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/posts/popular"))
                .andExpect(jsonPath("$.items.length()").value(0));

        // owner sees own posts
        mvc.perform(get("/api/users/carol/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].isMine").value(true));

        // follow -> PENDING -> accept -> visible
        mvc.perform(post("/api/users/carol/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(jsonPath("$.followStatus").value("PENDING"));
        mvc.perform(get("/api/users/me/follow-requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(jsonPath("$[0].username").value("dave"));
        mvc.perform(get("/api/users/carol/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/users/me/follow-requests/dave/accept").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/carol/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.hasNext").value(false));
        mvc.perform(get("/api/users/carol").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(jsonPath("$.followStatus").value("ACCEPTED"))
                .andExpect(jsonPath("$.canViewPosts").value(true))
                .andExpect(jsonPath("$.followerCount").value(1));
        // movie page includes private authors the viewer follows
        mvc.perform(get("/api/movies/" + MATRIX + "/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/movies/" + MATRIX + "/posts"))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void goingPublicAutoAcceptsPendingAndUpdatesPostPrivacy() throws Exception {
        String carol = register("carol");
        String dave = register("dave");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}")).andExpect(status().isOk());
        createPost(carol);
        mvc.perform(post("/api/users/carol/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(jsonPath("$.followStatus").value("PENDING"));

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":false}"))
                .andExpect(jsonPath("$.isPrivate").value(false))
                .andExpect(jsonPath("$.followerCount").value(1));
        mvc.perform(get("/api/users/dave"))
                .andExpect(jsonPath("$.followingCount").value(1));
        assertThat(mongo.findAll(Post.class)).allMatch(p -> !p.isAuthorIsPrivate());
        mvc.perform(get("/api/posts/popular?window=day"))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void feedTagsFollowingAndPopularWithoutDuplicates() throws Exception {
        String neo = register("neo");
        String trinity = register("trinity");
        String smith = register("smith");
        createPost(trinity);
        createPost(smith);
        createPost(neo);
        mvc.perform(post("/api/users/trinity/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo))
                .andExpect(jsonPath("$.followStatus").value("ACCEPTED"));

        String res = mvc.perform(get("/api/feed?size=10").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andReturn().getResponse().getContentAsString();
        JsonNode items = json.readTree(res).get("items");
        assertThat(items.get(0).get("author").get("username").asText()).isEqualTo("neo");
        assertThat(items.get(0).get("source").asText()).isEqualTo("FOLLOWING");
        assertThat(items.get(1).get("author").get("username").asText()).isEqualTo("trinity");
        assertThat(items.get(1).get("source").asText()).isEqualTo("FOLLOWING");
        assertThat(items.get(2).get("author").get("username").asText()).isEqualTo("smith");
        assertThat(items.get(2).get("source").asText()).isEqualTo("POPULAR");
    }

    @Test
    void commentsWithRepliesAndSoftDelete() throws Exception {
        String neo = register("neo");
        String smith = register("smith");
        String postId = createPost(neo);

        String c = mvc.perform(post("/api/posts/" + postId + "/comments").header(HttpHeaders.AUTHORIZATION, "Bearer " + smith)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"body\":\"Nice\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isMine").value(true))
                .andExpect(jsonPath("$.replies.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        String commentId = json.readTree(c).get("id").asText();
        mvc.perform(post("/api/posts/" + postId + "/comments").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("body", "Thanks", "parentCommentId", commentId))))
                .andExpect(status().isCreated());

        mvc.perform(delete("/api/comments/" + commentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + neo))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWNER"));
        mvc.perform(delete("/api/comments/" + commentId).header(HttpHeaders.AUTHORIZATION, "Bearer " + smith))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/posts/" + postId + "/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].deleted").value(true))
                .andExpect(jsonPath("$.items[0].body").value("[deleted]"))
                .andExpect(jsonPath("$.items[0].isMine").value(false))
                .andExpect(jsonPath("$.items[0].replies[0].body").value("Thanks"))
                .andExpect(jsonPath("$.items[0].replies[0].author.username").value("neo"));
        mvc.perform(get("/api/posts/" + postId)).andExpect(jsonPath("$.commentCount").value(1));
    }

    @Test
    void suggestionsExcludeSelfFollowedRequestedAndPrivate() throws Exception {
        String neo = register("neo");
        String trinity = register("trinity");
        register("smith");
        String carol = register("carol");
        String oracle = register("oracle");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}")).andExpect(status().isOk());
        mvc.perform(post("/api/users/trinity/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo))
                .andExpect(jsonPath("$.followStatus").value("ACCEPTED"));
        mvc.perform(post("/api/users/smith/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + oracle))
                .andExpect(status().isOk());
        mvc.perform(post("/api/users/smith/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + trinity))
                .andExpect(status().isOk());

        // anonymous: all public accounts, by followerCount desc (smith has 2, trinity 1)
        mvc.perform(get("/api/users/suggestions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].username").value("smith"))
                .andExpect(jsonPath("$[1].username").value("trinity"))
                .andExpect(jsonPath("$[0].followerCount").value(2))
                .andExpect(jsonPath("$[0].isPrivate").value(false));

        // neo: not neo, not trinity (followed), not carol (private)
        String res = mvc.perform(get("/api/users/suggestions").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> names = new java.util.ArrayList<>();
        json.readTree(res).forEach(n -> names.add(n.get("username").asText()));
        assertThat(names).containsExactly("smith", "oracle");
    }

    @Test
    void followersOfPrivateAccountRequireAcceptedFollow() throws Exception {
        String carol = register("carol");
        String dave = register("dave");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}")).andExpect(status().isOk());

        mvc.perform(get("/api/users/carol/followers"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        mvc.perform(get("/api/users/carol/following").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PRIVATE_ACCOUNT"));
        mvc.perform(get("/api/users/carol/followers").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(status().isOk());

        // cancel a PENDING request via DELETE: idempotent, counters untouched
        mvc.perform(post("/api/users/carol/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                .andExpect(jsonPath("$.followStatus").value("PENDING"));
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete("/api/users/carol/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + dave))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.followStatus").value("NONE"));
        }
        mvc.perform(get("/api/users/me/follow-requests").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/users/carol")).andExpect(jsonPath("$.followerCount").value(0));
        mvc.perform(get("/api/users/dave")).andExpect(jsonPath("$.followingCount").value(0));

        // public account lists are open
        mvc.perform(get("/api/users/dave/followers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.hasNext").value(false));
    }

    @Test
    void passwordLengthUpTo100IsAccepted() throws Exception {
        String longPw = "p".repeat(100);
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "longpw", "email", "longpw@test.dev",
                                "password", longPw, "displayName", "Long"))))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("usernameOrEmail", "longpw", "password", longPw))))
                .andExpect(status().isOk());
        // differs only after byte 72: must NOT match
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("usernameOrEmail", "longpw", "password", "p".repeat(99) + "q"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", "toolong", "email", "toolong@test.dev",
                                "password", "p".repeat(101), "displayName", "Long"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void emptyPostBodyIsRejected() throws Exception {
        String token = register("neo");
        mvc.perform(multipart("/api/posts").file(postPart(Map.of("tmdbId", MATRIX, "title", "t", "body", "",
                                "rating", 5, "imageType", "NONE")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.body").exists());
    }

    @Test
    void reviewStatsCountOnlyPublicAuthors() throws Exception {
        String neo = register("neo");
        String carol = register("carol");
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}")).andExpect(status().isOk());
        createPost(neo); // rating 9, public
        mvc.perform(multipart("/api/posts").file(postPart(Map.of("tmdbId", MATRIX, "title", "meh", "body", "meh",
                                "rating", 1, "imageType", "NONE")))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/movies/" + MATRIX).header(HttpHeaders.AUTHORIZATION, "Bearer " + carol))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.avgRating").value(9.0));

        when(tmdb.trendingWeek()).thenReturn(List.of(new TmdbModels.MovieResult(MATRIX, "The Matrix", "The Matrix",
                "1999-03-30", "", "/matrix.jpg", null, 8.2)));
        when(tmdb.searchMovies("matrix", 1)).thenReturn(new TmdbModels.PagedResults(1, List.of(
                new TmdbModels.MovieResult(MATRIX, "The Matrix", "The Matrix", "1999-03-30", "", "/matrix.jpg",
                        null, 8.2)), 1, 1));
        mvc.perform(get("/api/movies/search?q=matrix"))
                .andExpect(jsonPath("$.items[0].reviewCount").value(1))
                .andExpect(jsonPath("$.items[0].avgRating").value(9.0))
                .andExpect(jsonPath("$.hasNext").value(false));
        // stats are computed per request even when the trending list comes from the 1 h cache
        mvc.perform(get("/api/movies/trending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.tmdbId == 603)].reviewCount").value(1))
                .andExpect(jsonPath("$[?(@.tmdbId == 603)].avgRating").value(9.0));

        // carol goes public -> her review now counts
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":false}")).andExpect(status().isOk());
        mvc.perform(get("/api/movies/" + MATRIX))
                .andExpect(jsonPath("$.reviewCount").value(2))
                .andExpect(jsonPath("$.avgRating").value(5.0));
    }

    @Test
    void createPostRequiresMultipart() throws Exception {
        String token = register("neo");
        mvc.perform(post("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(body(postBody(MATRIX, "NONE"))))
                .andExpect(status().isUnsupportedMediaType());
        assertThat(mongo.count(new Query(), Post.class)).isZero();
    }

    @Test
    void unsupportedMethodReturns405MethodNotAllowed() throws Exception {
        String token = register("neo");
        mvc.perform(patch("/api/posts/popular").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void profileEditDoesNotClobberCounters() throws Exception {
        String neo = register("neo");
        String trinity = register("trinity");
        createPost(neo);
        mvc.perform(post("/api/users/neo/follow").header(HttpHeaders.AUTHORIZATION, "Bearer " + trinity))
                .andExpect(status().isOk());
        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + neo)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"The One\",\"bio\":\"Whoa\",\"isPrivate\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("The One"))
                .andExpect(jsonPath("$.bio").value("Whoa"))
                .andExpect(jsonPath("$.isPrivate").value(true))
                .andExpect(jsonPath("$.postCount").value(1))
                .andExpect(jsonPath("$.followerCount").value(1));
    }

    // ------------------------------------------------------------------ helpers

    private String register(String username) throws Exception {
        String res = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("username", username, "email", username + "@test.dev",
                                "password", "password123", "displayName", username))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private String createPost(String token) throws Exception {
        String res = createPostRequest(token, MATRIX, "NONE")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("id").asText();
    }

    private ResultActions createPostRequest(String token, long tmdbId, String imageType) throws Exception {
        MockHttpServletRequestBuilder req = multipart("/api/posts")
                .file(postPart(postBody(tmdbId, imageType)))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return mvc.perform(req);
    }

    private Map<String, Object> postBody(long tmdbId, String imageType) {
        return Map.of("tmdbId", tmdbId, "title", "Red pill review", "body", "Whoa. Great movie.",
                "rating", 9, "containsSpoilers", false, "imageType", imageType);
    }

    private MockMultipartFile postPart(Map<String, Object> body) throws Exception {
        return new MockMultipartFile("post", "", MediaType.APPLICATION_JSON_VALUE, json.writeValueAsBytes(body));
    }

    private String body(Object o) throws Exception {
        return json.writeValueAsString(o);
    }
}

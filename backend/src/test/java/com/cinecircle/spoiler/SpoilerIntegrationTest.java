package com.cinecircle.spoiler;

import com.cinecircle.AiIntegrationSupport;
import com.cinecircle.llm.LlmException;
import com.cinecircle.post.Post;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SpoilerIntegrationTest extends AiIntegrationSupport {

    static final String SPOILER = "{\"isSpoiler\":true,\"titleIsSpoiler\":true,\"confidence\":0.92,"
            + "\"reason\":\"Reveals the ending\"}";
    static final String CLEAN = "{\"isSpoiler\":false,\"titleIsSpoiler\":false,\"confidence\":0.9,\"reason\":\"\"}";

    @Autowired SpoilerCheckService checks;

    private void stubDetector(String reply) {
        when(llm.completeJson(anyString(), anyString(), eq(SpoilerDetector.MAX_TOKENS), any())).thenReturn(reply);
    }

    // ------------------------------------------------------------------ POST /posts

    @Test
    void createWithAiSpoilerIsFlaggedInResponse() throws Exception {
        String token = register("neo");
        stubDetector(SPOILER);
        createPost(token, "He was the one all along", "Great movie.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.containsSpoilers").value(false))
                .andExpect(jsonPath("$.spoiler.flagged").value(true))
                .andExpect(jsonPath("$.spoiler.source").value("AI"))
                .andExpect(jsonPath("$.spoiler.aiStatus").value("SPOILER"))
                .andExpect(jsonPath("$.spoiler.aiReason").value("Reveals the ending"))
                .andExpect(jsonPath("$.spoiler.titleHidden").value(true))
                // full title/body are still sent; hiding is client-side
                .andExpect(jsonPath("$.title").value("He was the one all along"))
                .andExpect(jsonPath("$.body").value("Great movie."));
        verify(llm).completeJson(anyString(), anyString(), eq(150), eq(Duration.ofSeconds(8)));
        Post stored = mongo.findOne(new Query(), Post.class);
        assertThat(stored.getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.SPOILER);
        assertThat(stored.getAiSpoilerConfidence()).isEqualTo(0.92);
        assertThat(stored.getAiSpoilerCheckedAt()).isNotNull();

        // same object on list endpoints
        mvc.perform(get("/api/movies/" + MATRIX + "/posts"))
                .andExpect(jsonPath("$.items[0].spoiler.source").value("AI"))
                .andExpect(jsonPath("$.items[0].spoiler.titleHidden").value(true));
    }

    @Test
    void createCleanAndAuthorFlagIsKept() throws Exception {
        String token = register("neo");
        stubDetector(CLEAN);
        createPost(token, "Loved it", "Great visuals.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spoiler.flagged").value(false))
                .andExpect(jsonPath("$.spoiler.source").value("NONE"))
                .andExpect(jsonPath("$.spoiler.aiStatus").value("CLEAN"))
                .andExpect(jsonPath("$.spoiler.aiReason").doesNotExist())
                .andExpect(jsonPath("$.spoiler.titleHidden").value(false));

        // AI says CLEAN but the author ticked the box: still flagged by AUTHOR
        createPost(token, "Loved it too", "Great visuals.", true)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.containsSpoilers").value(true))
                .andExpect(jsonPath("$.spoiler.flagged").value(true))
                .andExpect(jsonPath("$.spoiler.source").value("AUTHOR"))
                .andExpect(jsonPath("$.spoiler.aiStatus").value("CLEAN"));
    }

    @Test
    void createWhenDetectorTimesOutStaysPendingAndIs201() throws Exception {
        String token = register("neo");
        when(llm.completeJson(anyString(), anyString(), eq(150), any()))
                .thenThrow(new LlmException("LLM request failed: timeout after 8000 ms"));
        createPost(token, "Loved it", "Great visuals.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spoiler.aiStatus").value("PENDING"))
                .andExpect(jsonPath("$.spoiler.flagged").value(false));
        Post stored = mongo.findOne(new Query(), Post.class);
        assertThat(stored.getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.PENDING);
        assertThat(stored.getAiSpoilerAttempts()).isEqualTo(1);
    }

    @Test
    void createWithInvalidJsonStaysPending() throws Exception {
        String token = register("neo");
        stubDetector("I think it is fine");
        createPost(token, "Loved it", "Great visuals.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spoiler.aiStatus").value("PENDING"));
    }

    @Test
    void createWithLlmDisabledIsSkipped() throws Exception {
        when(llm.isEnabled()).thenReturn(false);
        String token = register("neo");
        createPost(token, "Loved it", "Great visuals.", false)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.spoiler.aiStatus").value("SKIPPED"))
                .andExpect(jsonPath("$.spoiler.flagged").value(false));
        verify(llm, never()).completeJson(anyString(), anyString(), anyInt(), any());
        // the job does nothing either
        assertThat(checks.processBatch()).isZero();
    }

    // ------------------------------------------------------------------ job

    @Test
    void jobRetriesPendingAndGivesUpAfterThreeFailures() {
        Post p = insertPost("neo", false, false, AiSpoilerStatus.PENDING, 8, "T", "B",
                Instant.now().minus(Duration.ofMinutes(5)));
        when(llm.completeJson(anyString(), anyString(), eq(150), any())).thenThrow(new LlmException("HTTP 529"));

        assertThat(checks.processBatch()).isEqualTo(1);
        assertThat(reload(p).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.PENDING);
        assertThat(reload(p).getAiSpoilerAttempts()).isEqualTo(1);
        checks.processBatch();
        assertThat(reload(p).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.PENDING);
        checks.processBatch();
        Post done = reload(p);
        assertThat(done.getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.SKIPPED);
        assertThat(done.getAiSpoilerAttempts()).isEqualTo(3);
        // no further attempts
        assertThat(checks.processBatch()).isZero();
        verify(llm, times(3)).completeJson(anyString(), anyString(), eq(150), any());
    }

    @Test
    void jobRetrySucceeds() {
        Post p = insertPost("neo", false, false, AiSpoilerStatus.PENDING, 8, "T", "B",
                Instant.now().minus(Duration.ofMinutes(5)));
        stubDetector(SPOILER);
        checks.processBatch();
        assertThat(reload(p).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.SPOILER);
        assertThat(reload(p).getAiSpoilerReason()).isEqualTo("Reveals the ending");
    }

    @Test
    void jobSkipsPendingYoungerThan30Seconds() {
        insertPost("neo", false, false, AiSpoilerStatus.PENDING, 8, "T", "B", Instant.now());
        stubDetector(CLEAN);
        assertThat(checks.processBatch()).isZero();
    }

    @Test
    void jobProcessesPreV2PostsWithoutTheField() {
        // raw pre-v2 document: no aiSpoiler* fields at all
        mongo.getCollection("posts").insertOne(new Document()
                .append("authorId", "legacy-author").append("authorUsername", "legacy")
                .append("authorIsPrivate", false).append("tmdbId", MATRIX).append("movieTitle", "The Matrix")
                .append("title", "Old review").append("body", "Old body").append("rating", 7)
                .append("containsSpoilers", false).append("imageType", "NONE")
                .append("upvoteCount", 0L).append("commentCount", 0L).append("hotScore", 1.0)
                .append("createdAt", Date.from(Instant.now().minus(Duration.ofDays(400)))));
        Post legacy = mongo.findOne(new Query(), Post.class);
        assertThat(legacy.getAiSpoilerStatus()).isNull();
        assertThat(SpoilerInfo.of(legacy).aiStatus()).isEqualTo(AiSpoilerStatus.UNCHECKED);

        stubDetector(CLEAN);
        assertThat(checks.processBatch()).isEqualTo(1);
        assertThat(reload(legacy).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.CLEAN);
    }

    @Test
    void preV2PostIsServedAsUnchecked() throws Exception {
        insertPost("legacy", false, false, null, 7, "Old", "Old body", Instant.now().minus(Duration.ofDays(1)));
        mvc.perform(get("/api/movies/" + MATRIX + "/posts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].spoiler.aiStatus").value("UNCHECKED"))
                .andExpect(jsonPath("$.items[0].spoiler.flagged").value(false))
                .andExpect(jsonPath("$.items[0].spoiler.source").value("NONE"));
    }

    @Test
    void jobBatchSizeIsRespectedAndPendingGoesFirst() {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        for (int i = 0; i < 25; i++) {
            insertPost("u" + i, false, false, AiSpoilerStatus.UNCHECKED, 5, "T" + i, "B", old.plusSeconds(i));
        }
        Post pending = insertPost("p", false, false, AiSpoilerStatus.PENDING, 5, "P", "B",
                Instant.now().minus(Duration.ofMinutes(2)));
        stubDetector(CLEAN);

        assertThat(checks.processBatch()).isEqualTo(20);
        assertThat(reload(pending).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.CLEAN);
        long stillUnchecked = mongo.count(Query.query(Criteria.where("aiSpoilerStatus").is("UNCHECKED")), Post.class);
        assertThat(stillUnchecked).isEqualTo(6);
        verify(llm, times(20)).completeJson(anyString(), anyString(), eq(150), any());

        assertThat(checks.processBatch()).isEqualTo(6);
        assertThat(checks.processBatch()).isZero();
    }

    @Test
    void jobNeverOverwritesAFinishedResult() {
        Post p = insertPost("neo", false, true, AiSpoilerStatus.SPOILER, 8, "T", "B",
                Instant.now().minus(Duration.ofMinutes(5)));
        stubDetector(CLEAN);
        assertThat(checks.processBatch()).isZero();
        assertThat(reload(p).getAiSpoilerStatus()).isEqualTo(AiSpoilerStatus.SPOILER);
    }

    @Test
    void jobPromptUsesMovieOverviewAndNoUsernames() {
        mongo.insert(com.cinecircle.movie.Movie.builder().tmdbId(MATRIX).title("The Matrix").releaseYear(1999)
                .overview("A hacker learns the nature of his reality.").cachedAt(Instant.now()).build());
        insertPost("secret_username", false, false, null, 8, "T", "B", Instant.now().minus(Duration.ofDays(1)));
        stubDetector(CLEAN);
        checks.processBatch();
        org.mockito.ArgumentCaptor<String> user = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llm).completeJson(anyString(), user.capture(), eq(150), any());
        assertThat(user.getValue()).contains("A hacker learns the nature of his reality.")
                .doesNotContain("secret_username").doesNotContain("id-");
    }

    private Post reload(Post p) {
        return mongo.findById(p.getId(), Post.class);
    }
}

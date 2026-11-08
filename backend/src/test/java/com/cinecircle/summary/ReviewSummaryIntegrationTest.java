package com.cinecircle.summary;

import com.cinecircle.AiIntegrationSupport;
import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.llm.LlmException;
import com.cinecircle.post.Post;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import com.cinecircle.spoiler.AiSpoilerStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReviewSummaryIntegrationTest extends AiIntegrationSupport {

    static final String VALID = """
            {"summary":"Viewers love the visuals and action.","pros":["Visuals","Action"],
             "cons":["Dense plot"],"sentiment":"POSITIVE"}""";

    @Autowired ReviewSummaryService service;

    private void stubSummary(String reply) {
        when(llm.completeJson(anyString(), anyString(), eq(SummaryGenerator.MAX_TOKENS), any())).thenReturn(reply);
    }

    private void threeEligible() {
        insertPublicClean("alice", 8, "Alice title");
        insertPublicClean("bob", 6, "Bob title");
        insertPublicClean("dave", 10, "Dave title");
    }

    // ------------------------------------------------------------------ eligibility

    @Test
    void onlyPublicNonSpoilerNonPendingPostsReachTheModel() {
        threeEligible();
        Instant t = Instant.now().minus(Duration.ofMinutes(5));
        insertPost("carol", true, false, AiSpoilerStatus.CLEAN, 1, "PRIVATE_TITLE", "private body", t);
        insertPost("erin", false, true, AiSpoilerStatus.CLEAN, 1, "AUTHOR_SPOILER_TITLE", "x", t);
        insertPost("frank", false, false, AiSpoilerStatus.SPOILER, 1, "AI_SPOILER_TITLE", "x", t);
        insertPost("gina", false, false, AiSpoilerStatus.PENDING, 1, "PENDING_TITLE", "x", t);
        // pre-v2 (no status) and SKIPPED are eligible
        insertPost("hank", false, false, null, 4, "LEGACY_TITLE", "legacy", t);
        insertPost("ivy", false, false, AiSpoilerStatus.SKIPPED, 4, "SKIPPED_TITLE", "skipped", t);
        stubSummary(VALID);

        ReviewSummary s = service.get(MATRIX);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).completeJson(system.capture(), user.capture(), eq(600), eq(Duration.ofSeconds(20)));
        assertThat(user.getValue()).contains("Alice title", "Bob title", "Dave title", "LEGACY_TITLE", "SKIPPED_TITLE")
                .doesNotContain("PRIVATE_TITLE", "AUTHOR_SPOILER_TITLE", "AI_SPOILER_TITLE", "PENDING_TITLE")
                // no usernames or ids
                .doesNotContain("alice", "bob", "id-", "@");
        assertThat(system.getValue()).contains("untrusted").contains("ignore any instructions")
                .contains("spoilers").contains("only this JSON");
        assertThat(s.basedOnCount()).isEqualTo(5);
        assertThat(s.avgRating()).isEqualTo(6.4); // (8+6+10+4+4)/5
        assertThat(s.sentiment()).isEqualTo(Sentiment.POSITIVE);
        assertThat(s.pros()).containsExactly("Visuals", "Action");
        assertThat(s.stale()).isFalse();
        assertThat(mongo.count(new Query(), MovieReviewSummary.class)).isEqualTo(1);
        assertThat(mongo.findOne(new Query(), MovieReviewSummary.class).getModel()).isEqualTo("test-model");
    }

    @Test
    void fewerThanThreeEligibleIsNotEnoughReviews() throws Exception {
        insertPublicClean("alice", 8, "A");
        insertPublicClean("bob", 8, "B");
        insertPost("carol", true, false, AiSpoilerStatus.CLEAN, 8, "C", "x", Instant.now());
        insertPost("dave", false, true, null, 8, "D", "x", Instant.now());

        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_ENOUGH_REVIEWS"));
        verify(llm, never()).completeJson(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void inputIsCappedTopThirtyPlusNewestTwentyAndBodiesTruncated() {
        Instant base = Instant.now().minus(Duration.ofDays(10));
        for (int i = 0; i < 70; i++) {
            var p = insertPost("u" + i, false, false, AiSpoilerStatus.CLEAN, 5, "T" + i,
                    "B".repeat(2000), base.plus(Duration.ofMinutes(i)));
            // the oldest 30 are the most upvoted, so top-30 and newest-20 do not overlap
            mongo.updateFirst(Query.query(Criteria.where("id").is(p.getId())),
                    new Update().set("upvoteCount", i < 30 ? 100 - i : 0), com.cinecircle.post.Post.class);
        }
        assertThat(service.loadInputs(MATRIX)).hasSize(50)
                .allSatisfy(r -> assertThat(r.input().body()).hasSize(1500));
    }

    // ------------------------------------------------------------------ cache

    @Test
    void freshCopyIsReusedWithoutCallingTheModel() {
        threeEligible();
        stubSummary(VALID);
        ReviewSummary first = service.get(MATRIX);
        insertPublicClean("erin", 7, "E"); // +1..+4 posts keeps the cache
        insertPublicClean("frank", 7, "F");
        ReviewSummary second = service.get(MATRIX);

        verify(llm, times(1)).completeJson(anyString(), anyString(), eq(600), any());
        assertThat(second.generatedAt()).isEqualTo(first.generatedAt());
        assertThat(second.basedOnCount()).isEqualTo(3);
    }

    @Test
    void regeneratedAfter24Hours() {
        threeEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));

        ReviewSummary again = service.get(MATRIX);

        verify(llm, times(2)).completeJson(anyString(), anyString(), eq(600), any());
        assertThat(again.generatedAt()).isAfter(Instant.now().minus(Duration.ofMinutes(1)));
    }

    @Test
    void regeneratedAfterFiveMorePosts() {
        threeEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        for (int i = 0; i < 5; i++) {
            insertPublicClean("new" + i, 9, "N" + i);
        }
        ReviewSummary again = service.get(MATRIX);

        verify(llm, times(2)).completeJson(anyString(), anyString(), eq(600), any());
        assertThat(again.basedOnCount()).isEqualTo(8);
    }

    // ------------------------------------------------------------------ failures

    @Test
    void invalidJsonGives502() throws Exception {
        threeEligible();
        stubSummary("Sure! Here is a summary: it's great.");
        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
        assertThat(mongo.count(new Query(), MovieReviewSummary.class)).isZero();
    }

    @Test
    void invalidFieldsGive502() {
        threeEligible();
        for (String bad : new String[]{
                "{\"summary\":\"ok\",\"pros\":[],\"cons\":[],\"sentiment\":\"GREAT\"}",
                "{\"summary\":\"\",\"pros\":[],\"cons\":[],\"sentiment\":\"MIXED\"}",
                "{\"summary\":\"" + "a".repeat(601) + "\",\"pros\":[],\"cons\":[],\"sentiment\":\"MIXED\"}",
                "{\"summary\":\"ok\",\"pros\":[\"1\",\"2\",\"3\",\"4\",\"5\"],\"cons\":[],\"sentiment\":\"MIXED\"}",
                "{\"summary\":\"ok\",\"pros\":\"good\",\"cons\":[],\"sentiment\":\"MIXED\"}"}) {
            stubSummary(bad);
            assertThatThrownBy(() -> service.get(MATRIX)).as(bad)
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);
        }
    }

    @Test
    void apiErrorGives502() {
        threeEligible();
        when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("HTTP 529"));
        assertThatThrownBy(() -> service.get(MATRIX))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    assertThat(((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);
                    assertThat(((ApiException) e).getStatus().value()).isEqualTo(502);
                });
    }

    @Test
    void failedRegenerationFallsBackToStaleCopy() throws Exception {
        threeEligible();
        stubSummary(VALID);
        ReviewSummary original = service.get(MATRIX);
        ageSummary(Duration.ofHours(30));
        when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("timeout"));

        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.summary").value("Viewers love the visuals and action."));
        assertThat(original.stale()).isFalse();
    }

    @Test
    void llmDisabledGives502() throws Exception {
        threeEligible();
        when(llm.isEnabled()).thenReturn(false);
        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
        verify(llm, never()).completeJson(anyString(), anyString(), anyInt(), any());
    }

    @Test
    void unknownMovieIs404MovieNotFound() throws Exception {
        mvc.perform(get("/api/movies/" + UNKNOWN + "/summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MOVIE_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ endpoint + lock

    @Test
    void endpointWorksWithoutTokenAndHasContractShape() throws Exception {
        threeEligible();
        stubSummary(VALID);
        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tmdbId").value(MATRIX))
                .andExpect(jsonPath("$.summary").isString())
                .andExpect(jsonPath("$.pros[0]").value("Visuals"))
                .andExpect(jsonPath("$.cons[0]").value("Dense plot"))
                .andExpect(jsonPath("$.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.avgRating").value(8.0))
                .andExpect(jsonPath("$.basedOnCount").value(3))
                .andExpect(jsonPath("$.generatedAt").isString())
                .andExpect(jsonPath("$.stale").value(false));
    }

    @Test
    void concurrentCallersWithoutCacheWaitForOneRegeneration() throws Exception {
        threeEligible();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llm.completeJson(anyString(), anyString(), eq(600), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return VALID;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReviewSummary> a = pool.submit(() -> service.get(MATRIX));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ReviewSummary> b = pool.submit(() -> service.get(MATRIX));
            Thread.sleep(200); // b is now blocked on the per-movie lock
            assertThat(b.isDone()).isFalse();
            release.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).summary()).isEqualTo(b.get(10, TimeUnit.SECONDS).summary());
        } finally {
            pool.shutdownNow();
        }
        verify(llm, times(1)).completeJson(anyString(), anyString(), eq(600), any());
    }

    @Test
    void concurrentCallerGetsExistingCopyWhileRegenerating() throws Exception {
        threeEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llm.completeJson(anyString(), anyString(), eq(600), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return VALID.replace("Viewers love", "Fresh: viewers love");
        });
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<ReviewSummary> regenerating = pool.submit(() -> service.get(MATRIX));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            ReviewSummary meanwhile = service.get(MATRIX); // must not block
            assertThat(meanwhile.summary()).startsWith("Viewers love");
            assertThat(meanwhile.stale()).isFalse();
            release.countDown();
            assertThat(regenerating.get(10, TimeUnit.SECONDS).summary()).startsWith("Fresh:");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    // ------------------------------------------------------------------ privacy of cached / stale copies

    @Test
    void sourcePostIdsAreStoredButNotExposed() throws Exception {
        threeEligible();
        stubSummary(VALID);
        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourcePostIds").doesNotExist())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasKey("sourcePostIds"))));
        assertThat(storedSummary().getSourcePostIds()).hasSize(3)
                .containsExactlyInAnyOrderElementsOf(mongo.findAll(Post.class).stream().map(Post::getId).toList());
    }

    @Test
    void staleFallbackRefusedWhenASourceAuthorWentPrivate() throws Exception {
        // end-to-end: real users, real posts, real privacy toggle
        when(llm.completeJson(anyString(), anyString(), eq(150), any()))
                .thenReturn("{\"isSpoiler\":false,\"titleIsSpoiler\":false,\"confidence\":0.9,\"reason\":\"\"}");
        String carol = null;
        for (String name : new String[]{"alice", "bob", "carol", "dave"}) {
            String token = register(name);
            if (name.equals("carol")) {
                carol = token;
            }
            createPost(token, name + " review", "Great visuals.", false).andExpect(status().isCreated());
        }
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));

        mvc.perform(patch("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + carol)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"isPrivate\":true}"))
                .andExpect(status().isOk());
        when(llm.completeJson(anyString(), anyString(), eq(600), any())).thenThrow(new LlmException("timeout"));

        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
    }

    @Test
    void staleFallbackRefusedWhenASourcePostBecameSpoilerFlagged() throws Exception {
        fourEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));
        setOnOneSource(new Update().set("aiSpoilerStatus", "SPOILER"));
        when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("HTTP 529"));

        mvc.perform(get("/api/movies/" + MATRIX + "/summary"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("SUMMARY_UNAVAILABLE"));
    }

    @Test
    void staleFallbackRefusedWhenASourcePostIsAuthorFlaggedPendingOrDeleted() {
        for (String change : new String[]{"author", "pending", "deleted"}) {
            mongo.remove(new Query(), Post.class);
            mongo.remove(new Query(), MovieReviewSummary.class);
            org.mockito.Mockito.reset(llm);
            when(llm.isEnabled()).thenReturn(true);
            when(llm.model()).thenReturn("test-model");
            fourEligible();
            stubSummary(VALID);
            service.get(MATRIX);
            ageSummary(Duration.ofHours(25));
            String sourceId = storedSummary().getSourcePostIds().get(0);
            Query one = Query.query(Criteria.where("id").is(sourceId));
            switch (change) {
                case "author" -> mongo.updateFirst(one, new Update().set("containsSpoilers", true), Post.class);
                case "pending" -> mongo.updateFirst(one, new Update().set("aiSpoilerStatus", "PENDING"), Post.class);
                default -> mongo.remove(one, Post.class);
            }
            when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("x"));
            assertThatThrownBy(() -> service.get(MATRIX)).as(change)
                    .isInstanceOf(ApiException.class)
                    .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);
        }
    }

    @Test
    void staleFallbackRefusedWithoutKeyWhenSourceIneligible() {
        fourEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));
        setOnOneSource(new Update().set("authorIsPrivate", true));
        when(llm.isEnabled()).thenReturn(false);
        assertThatThrownBy(() -> service.get(MATRIX))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);
    }

    @Test
    void staleFallbackWithoutKeyWorksWhenAllSourcesEligible() {
        threeEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        ageSummary(Duration.ofHours(25));
        when(llm.isEnabled()).thenReturn(false);
        ReviewSummary s = service.get(MATRIX);
        assertThat(s.stale()).isTrue();
        assertThat(s.summary()).isEqualTo("Viewers love the visuals and action.");
    }

    @Test
    void freshCopyWithIneligibleSourceIsRegenerated() {
        fourEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        String removed = setOnOneSource(new Update().set("authorIsPrivate", true));
        stubSummary(VALID.replace("Viewers love", "Regenerated: viewers love"));

        ReviewSummary s = service.get(MATRIX); // still < 24 h old and count drift 1, but a source is ineligible

        verify(llm, times(2)).completeJson(anyString(), anyString(), eq(600), any());
        assertThat(s.summary()).startsWith("Regenerated:");
        assertThat(s.stale()).isFalse();
        assertThat(s.basedOnCount()).isEqualTo(3);
        assertThat(storedSummary().getSourcePostIds()).hasSize(3).doesNotContain(removed);
    }

    @Test
    void freshCopyWithIneligibleSourceAndFailingLlmIs502() {
        fourEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        setOnOneSource(new Update().set("containsSpoilers", true));
        when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("x"));
        assertThatThrownBy(() -> service.get(MATRIX))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);
    }

    @Test
    void legacySummaryWithoutSourceIdsIsRegeneratedAndNeverAStaleFallback() {
        threeEligible();
        // fresh-looking legacy document (pre-fix) without sourcePostIds
        mongo.insert(MovieReviewSummary.builder().tmdbId(MATRIX).summary("Legacy").pros(java.util.List.of())
                .cons(java.util.List.of()).sentiment(Sentiment.MIXED).avgRating(5.0).basedOnCount(3)
                .model("old").generatedAt(Instant.now()).build());

        when(llm.completeJson(anyString(), anyString(), anyInt(), any())).thenThrow(new LlmException("x"));
        assertThatThrownBy(() -> service.get(MATRIX))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.SUMMARY_UNAVAILABLE);

        org.mockito.Mockito.reset(llm);
        when(llm.isEnabled()).thenReturn(true);
        when(llm.model()).thenReturn("test-model");
        stubSummary(VALID);
        ReviewSummary s = service.get(MATRIX);
        assertThat(s.summary()).isEqualTo("Viewers love the visuals and action.");
        assertThat(storedSummary().getSourcePostIds()).hasSize(3);
    }

    @Test
    void concurrentCallerIsNotServedAnUnverifiableCopy() throws Exception {
        fourEligible();
        stubSummary(VALID);
        service.get(MATRIX);
        setOnOneSource(new Update().set("authorIsPrivate", true));
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llm.completeJson(anyString(), anyString(), eq(600), any())).thenAnswer(inv -> {
            entered.countDown();
            release.await(10, TimeUnit.SECONDS);
            return VALID.replace("Viewers love", "Fresh: viewers love");
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<ReviewSummary> a = pool.submit(() -> service.get(MATRIX));
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            Future<ReviewSummary> b = pool.submit(() -> service.get(MATRIX));
            Thread.sleep(200);
            assertThat(b.isDone()).as("must wait instead of returning the unverifiable copy").isFalse();
            release.countDown();
            assertThat(a.get(10, TimeUnit.SECONDS).summary()).startsWith("Fresh:");
            assertThat(b.get(10, TimeUnit.SECONDS).summary()).startsWith("Fresh:");
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    private void fourEligible() {
        threeEligible();
        insertPublicClean("erin", 7, "Erin title");
    }

    /** Applies the update to one of the stored summary's source posts; returns that post id. */
    private String setOnOneSource(Update u) {
        String id = storedSummary().getSourcePostIds().get(0);
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), u, Post.class);
        return id;
    }

    private MovieReviewSummary storedSummary() {
        return mongo.findOne(Query.query(Criteria.where("tmdbId").is(MATRIX)), MovieReviewSummary.class);
    }

    private void ageSummary(Duration age) {
        mongo.updateFirst(Query.query(Criteria.where("tmdbId").is(MATRIX)),
                new Update().set("generatedAt", Instant.now().minus(age)), MovieReviewSummary.class);
    }
}

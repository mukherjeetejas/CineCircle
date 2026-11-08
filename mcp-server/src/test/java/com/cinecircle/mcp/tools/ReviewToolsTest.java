package com.cinecircle.mcp.tools;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ReviewToolsTest extends ToolTestSupport {

    private static final String MOVIE_POSTS = API + "/movies/438631/posts";
    private static final String DUNE_DETAIL = "{\"tmdbId\":438631,\"title\":\"Dune\",\"releaseYear\":2021,\"posterUrl\":null,"
            + "\"tmdbVoteAverage\":7.8,\"reviewCount\":4,\"avgRating\":8.25,\"overview\":\"o\",\"backdropUrl\":null,\"genres\":[]}";
    private static final String SIMPLE_SUMMARY = "{\"tmdbId\":438631,\"summary\":\"Good.\",\"pros\":[],\"cons\":[],"
            + "\"sentiment\":\"POSITIVE\",\"avgRating\":8.0,\"basedOnCount\":3,\"generatedAt\":\"2026-11-08T09:30:00Z\","
            + "\"stale\":false}";

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class GetMovieReviews {

        @Test
        void defaults_sortTop_limit10_firstPage_andFormatsReview() {
            setUp(null);
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=10")).andExpect(method(HttpMethod.GET))
                    .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                    .andRespond(withSuccess(page(false, post("p1", "Great sand", "Loved the  visuals.", 9, false, noSpoiler())),
                            MediaType.APPLICATION_JSON));

            String out = reviewTools.getMovieReviews(438631L, null, null, null);

            assertThat(out).isEqualTo("""
                    Reviews of Dune (2021) (tmdbId 438631) - top, showing 1:

                    1. "Great sand" - 9/10 by @alice · 12 upvotes · 1 comment · 2026-11-01
                       Loved the visuals.
                       http://localhost:5173/posts/p1""");
        }

        @Test
        void sortNew_and_limitIsClampedTo20() {
            setUp(null);
            server.expect(requestTo(MOVIE_POSTS + "?sort=new&page=0&size=20"))
                    .andRespond(withSuccess(page(true, post("p1", "t", "b", 5, false, noSpoiler())), MediaType.APPLICATION_JSON));

            String out = reviewTools.getMovieReviews(438631L, "NEW", 500, false);
            assertThat(out).contains("- new, showing 1").endsWith("More reviews: http://localhost:5173/movies/438631");
        }

        @Test
        void limitBelow1_isClampedTo1() {
            setUp(null);
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=1"))
                    .andRespond(withSuccess(page(false), MediaType.APPLICATION_JSON));

            assertThat(reviewTools.getMovieReviews(438631L, "top", -3, false))
                    .startsWith("No CineCircle reviews you can see for tmdbId 438631 yet.");
        }

        @Test
        void invalidSort_isRejectedWithoutCall() {
            setUp(null);
            assertThat(toolError(() -> reviewTools.getMovieReviews(438631L, "best", 5, false)))
                    .isEqualTo("CineCircle error VALIDATION_ERROR: sort must be one of new, top.");
        }

        @Test
        void bodyIsCutTo300Chars() {
            setUp(null);
            String longBody = "a".repeat(800);
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=10"))
                    .andRespond(withSuccess(page(false, post("p1", "t", longBody, 5, false, noSpoiler())), MediaType.APPLICATION_JSON));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            String bodyLine = out.lines().filter(l -> l.trim().startsWith("aaa")).findFirst().orElseThrow().trim();
            assertThat(bodyLine).hasSize(300).endsWith("…");
        }

        @Test
        void authorFlagged_tagged_andBodyHidden_titleKept() {
            setUp(null);
            respondWith(post("p1", "Shocking end", "He dies at the end", 8, true, spoiler(true, "AUTHOR", "CLEAN", null, false)));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("1. \"Shocking end\" - 8/10")
                    .contains("[spoilers – flagged by author]")
                    .contains("   [spoilers hidden]\n")
                    .doesNotContain("He dies")
                    .contains("includeSpoilers=true");
        }

        @Test
        void aiFlagged_withReason_andTitleHidden() {
            setUp(null);
            respondWith(post("p1", "Vader is the father", "Big reveal", 9, false,
                    spoiler(true, "AI", "SPOILER", "Reveals a character's identity", true)));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("1. [spoilers hidden] - 9/10 by @alice")
                    .contains("[spoilers – flagged by AI: Reveals a character's identity]")
                    .doesNotContain("Vader").doesNotContain("Big reveal");
        }

        @Test
        void both_showsBothTags() {
            setUp(null);
            respondWith(post("p1", "t", "secret", 7, true, spoiler(true, "BOTH", "SPOILER", "Reveals the ending", false)));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("[spoilers – flagged by author] [spoilers – flagged by AI: Reveals the ending]")
                    .doesNotContain("secret");
        }

        @Test
        void pending_isTaggedAndHidden_evenThoughNotFlagged() {
            setUp(null);
            respondWith(post("p1", "Fresh take", "maybe spoilers", 6, false, spoiler(false, "NONE", "PENDING", null, false)));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("\"Fresh take\"").contains("[spoiler check pending]")
                    .contains("[spoilers hidden]").doesNotContain("maybe spoilers");
        }

        @Test
        void uncheckedAndSkipped_notFlagged_areShownWithoutTags() {
            setUp(null);
            respondWith(post("p1", "Old one", "pre v2 text", 6, false, spoiler(false, "NONE", "UNCHECKED", null, false)),
                    post("p2", "Skipped", "no llm text", 6, false, spoiler(false, "NONE", "SKIPPED", null, false)));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("pre v2 text").contains("no llm text")
                    .doesNotContain("[spoiler").doesNotContain("includeSpoilers");
        }

        @Test
        void includeSpoilersTrue_showsTitleAndBody_butKeepsTags() {
            setUp(null);
            respondWith(post("p1", "Vader is the father", "Big reveal", 9, false,
                            spoiler(true, "AI", "SPOILER", "Reveals a character's identity", true)),
                    post("p2", "Fresh", "pending body", 6, false, spoiler(false, "NONE", "PENDING", null, false)));

            String out = reviewTools.getMovieReviews(438631L, null, null, true);
            assertThat(out).contains("\"Vader is the father\"").contains("Big reveal").contains("pending body")
                    .contains("[spoilers – flagged by AI: Reveals a character's identity]")
                    .contains("[spoiler check pending]")
                    .doesNotContain("[spoilers hidden]");
        }

        @Test
        void noSpoilerObject_fallsBackToAuthorFlag_tagsAndHides() {
            setUp(null);
            respondWith(post("p1", "Author flagged", "the ending is", 8, true, "null"),
                    post("p2", "Unflagged", "plain text", 6, false, "null"));

            String out = reviewTools.getMovieReviews(438631L, null, null, false);
            assertThat(out).contains("1. \"Author flagged\" - 8/10")
                    .contains("[spoilers – flagged by author]")
                    .doesNotContain("the ending is")
                    .contains("plain text")
                    .contains("includeSpoilers=true");
            assertThat(out.split("\\[spoilers – flagged by author]", -1)).hasSize(2); // only the flagged one is tagged
            assertThat(out.split("\\[spoilers hidden]", -1)).hasSize(2);
        }

        @Test
        void noSpoilerObject_authorFlag_includeSpoilersShowsBody_keepsTag() {
            setUp(null);
            respondWith(post("p1", "Author flagged", "the ending is", 8, true, "null"));

            String out = reviewTools.getMovieReviews(438631L, null, null, true);
            assertThat(out).contains("the ending is").contains("[spoilers – flagged by author]")
                    .doesNotContain("[spoilers hidden]");
        }

        @Test
        void unknownMovie_mapsMovieNotFound() {
            setUp(null);
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=10"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(404, "MOVIE_NOT_FOUND", "Movie not found")));

            assertThat(toolError(() -> reviewTools.getMovieReviews(438631L, null, null, false))).contains("MOVIE_NOT_FOUND");
        }

        @Test
        void tokenSet_sendsBearer() {
            setUp(TOKEN);
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=10"))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andRespond(withSuccess(page(false), MediaType.APPLICATION_JSON));
            assertThat(reviewTools.getMovieReviews(438631L, null, null, false)).doesNotContain(TOKEN);
        }

        private void respondWith(String... items) {
            server.expect(requestTo(MOVIE_POSTS + "?sort=top&page=0&size=10"))
                    .andRespond(withSuccess(page(false, items), MediaType.APPLICATION_JSON));
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class GetReviewSummary {

        @Test
        void formatsSummary() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess("{\"tmdbId\":438631,\"summary\":\"Viewers love the scale.\",\"pros\":[\"Visuals\","
                            + "\"Score\"],\"cons\":[\"Slow start\"],\"sentiment\":\"POSITIVE\",\"avgRating\":8.25,"
                            + "\"basedOnCount\":4,\"generatedAt\":\"2026-11-08T09:30:00Z\",\"stale\":false}",
                            MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/movies/438631")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(DUNE_DETAIL, MediaType.APPLICATION_JSON));

            assertThat(reviewTools.getReviewSummary(438631L)).isEqualTo("""
                    What CineCircle thinks of Dune (2021) · tmdbId 438631 (AI-generated summary)
                    Sentiment: POSITIVE · avg rating 8.3/10 · based on 4 reviews · updated 2026-11-08 09:30 UTC
                    Viewers love the scale.
                    Pros:
                    - Visuals
                    - Score
                    Cons:
                    - Slow start
                    Movie page: http://localhost:5173/movies/438631""");
        }

        @Test
        void movieLookupFails_fallsBackToTmdbId_insteadOfFailing() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withSuccess(SIMPLE_SUMMARY, MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/movies/438631"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(502, "TMDB_UNAVAILABLE", "TMDB down")));

            assertThat(reviewTools.getReviewSummary(438631L))
                    .startsWith("What CineCircle thinks of tmdbId 438631 (AI-generated summary)\n")
                    .doesNotContain("TMDB_UNAVAILABLE");
        }

        @Test
        void movieLookupUnreachable_fallsBackToTmdbId() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withSuccess(SIMPLE_SUMMARY, MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/movies/438631")).andRespond(withException(new ConnectException("refused")));

            assertThat(reviewTools.getReviewSummary(438631L))
                    .startsWith("What CineCircle thinks of tmdbId 438631 (AI-generated summary)");
        }

        @Test
        void staleSummary_isMarked() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withSuccess("{\"tmdbId\":438631,\"summary\":\"Old.\",\"pros\":[],\"cons\":[],\"sentiment\":"
                            + "\"MIXED\",\"avgRating\":6.0,\"basedOnCount\":3,\"generatedAt\":\"2026-11-01T09:30:00Z\","
                            + "\"stale\":true}", MediaType.APPLICATION_JSON));
            server.expect(requestTo(API + "/movies/438631"))
                    .andRespond(withSuccess(DUNE_DETAIL, MediaType.APPLICATION_JSON));

            assertThat(reviewTools.getReviewSummary(438631L)).contains("Dune (2021)").contains("may be out of date")
                    .doesNotContain("Pros:");
        }

        @Test
        void notEnoughReviews_mapsCode() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(404, "NOT_ENOUGH_REVIEWS", "Need at least 3 reviews")));

            assertThat(toolError(() -> reviewTools.getReviewSummary(438631L)))
                    .isEqualTo("CineCircle error NOT_ENOUGH_REVIEWS: Need at least 3 reviews. "
                            + "A summary needs at least 3 public, spoiler-free reviews of this movie.");
        }

        @Test
        void summaryUnavailable_mapsCode() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withStatus(HttpStatus.BAD_GATEWAY).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(502, "SUMMARY_UNAVAILABLE", "Summary unavailable")));

            assertThat(toolError(() -> reviewTools.getReviewSummary(438631L))).startsWith("CineCircle error SUMMARY_UNAVAILABLE");
        }

        @Test
        void backendDown() {
            setUp(null);
            server.expect(requestTo(API + "/movies/438631/summary"))
                    .andRespond(withException(new ConnectException("refused")));

            assertThat(toolError(() -> reviewTools.getReviewSummary(438631L)))
                    .isEqualTo("CineCircle API is not reachable at http://localhost:8080/api");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class GetPopularReviews {

        @Test
        void defaults_windowWeek_limit10_showsMovie_andHidesSpoilers() {
            setUp(null);
            server.expect(requestTo(API + "/posts/popular?window=week&page=0&size=10")).andExpect(method(HttpMethod.GET))
                    .andRespond(withSuccess(page(true,
                            post("p1", "Clean", "nice film", 8, false, noSpoiler()),
                            post("p2", "Twist", "the twist is", 7, false, spoiler(true, "AI", "SPOILER", "Reveals the ending", true))),
                            MediaType.APPLICATION_JSON));

            String out = reviewTools.getPopularReviews(null, null);
            assertThat(out).startsWith("Popular reviews (week), showing 2:")
                    .contains("   Movie: Dune (2021) · tmdbId 438631")
                    .contains("nice film")
                    .contains("2. [spoilers hidden] - 7/10")
                    .contains("[spoilers – flagged by AI: Reveals the ending]")
                    .doesNotContain("the twist is").doesNotContain("Twist\"")
                    .contains("http://localhost:5173/posts/p2");
        }

        @Test
        void windowAndLimit_arePassed() {
            setUp(null);
            server.expect(requestTo(API + "/posts/popular?window=all&page=0&size=3"))
                    .andRespond(withSuccess(page(false), MediaType.APPLICATION_JSON));

            assertThat(reviewTools.getPopularReviews("all", 3)).isEqualTo("No popular CineCircle reviews for window 'all'.");
        }

        @Test
        void invalidWindow_isRejected() {
            setUp(null);
            assertThat(toolError(() -> reviewTools.getPopularReviews("month", 3)))
                    .isEqualTo("CineCircle error VALIDATION_ERROR: window must be one of all, day, week.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class CreateReview {

        private static String created(boolean authorFlag, String spoilerJson) {
            return "{\"id\":\"new1\",\"author\":" + user("bob", false)
                    + ",\"movie\":{\"tmdbId\":438631,\"title\":\"Dune\",\"releaseYear\":2021,\"posterUrl\":null}"
                    + ",\"title\":\"My take\",\"bodyPreview\":\"x\",\"body\":\"x\",\"rating\":8,\"containsSpoilers\":" + authorFlag
                    + ",\"spoiler\":" + spoilerJson + ",\"imageUrl\":null,\"upvoteCount\":0,\"commentCount\":0,"
                    + "\"upvotedByMe\":false,\"isMine\":true,\"createdAt\":\"2026-11-08T10:00:00Z\"}";
        }

        @Test
        void withoutToken_refuses_andMakesNoRequest() {
            setUp(null);
            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 8, false)))
                    .isEqualTo("Set CINECIRCLE_TOKEN to post as your account.");
            // server.verify() in @AfterEach fails if any request was made
        }

        @Test
        void postsMultipart_withTmdbPoster_andBearer_andReportsAiSpoiler() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts")).andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andExpect(header(HttpHeaders.CONTENT_TYPE, org.hamcrest.Matchers.startsWith("multipart/form-data")))
                    .andExpect(content().string(org.hamcrest.Matchers.allOf(
                            org.hamcrest.Matchers.containsString("name=\"post\""),
                            org.hamcrest.Matchers.containsString("Content-Type: application/json"),
                            org.hamcrest.Matchers.containsString("\"tmdbId\":438631"),
                            org.hamcrest.Matchers.containsString("\"title\":\"My take\""),
                            org.hamcrest.Matchers.containsString("\"body\":\"Great movie\""),
                            org.hamcrest.Matchers.containsString("\"rating\":8"),
                            org.hamcrest.Matchers.containsString("\"containsSpoilers\":false"),
                            org.hamcrest.Matchers.containsString("\"imageType\":\"TMDB_POSTER\""))))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body(created(false, spoiler(true, "AI", "SPOILER", "Reveals the ending", false))));

            String out = reviewTools.createReview(438631L, " My take ", "Great movie", 8, null);

            assertThat(out).startsWith("Posted. \"My take\" - 8/10 for Dune (2021)")
                    .contains("Link: http://localhost:5173/posts/new1")
                    .contains("Our AI marked it as containing spoilers (Reveals the ending), so it will be blurred for readers.")
                    .contains("[spoilers – flagged by AI: Reveals the ending]")
                    .doesNotContain(TOKEN);
        }

        @Test
        void clean_and_pending_and_skipped_results() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body(created(false, spoiler(false, "NONE", "CLEAN", null, false))));
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body(created(true, spoiler(true, "AUTHOR", "PENDING", null, false))));
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body(created(false, spoiler(false, "NONE", "SKIPPED", null, false))));

            assertThat(reviewTools.createReview(438631L, "My take", "b", 8, false))
                    .endsWith("Spoiler check: Our AI found no spoilers.");
            assertThat(reviewTools.createReview(438631L, "My take", "b", 8, true))
                    .contains("Spoiler check: still running").contains("You marked it as containing spoilers");
            assertThat(reviewTools.createReview(438631L, "My take", "b", 8, false))
                    .endsWith("Spoiler check: the AI check was skipped.");
        }

        @Test
        void unknownMovie_mapsMovieNotFound() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(400, "MOVIE_NOT_FOUND", "Unknown TMDB movie")));

            assertThat(toolError(() -> reviewTools.createReview(1L, "t", "b", 5, false)))
                    .startsWith("CineCircle error MOVIE_NOT_FOUND: Unknown TMDB movie.")
                    .contains("search_movies");
        }

        @Test
        void expiredToken_mapsUnauthorized_withoutEchoingToken() {
            setUp(TOKEN);
            // even if a (misbehaving) backend echoed the token, it must not reach the tool output
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(401, "UNAUTHORIZED", "Bad token " + TOKEN)));

            String err = toolError(() -> reviewTools.createReview(438631L, "t", "b", 5, false));
            assertThat(err).startsWith("CineCircle error UNAUTHORIZED").contains("CINECIRCLE_TOKEN").doesNotContain(TOKEN);
        }

        @Test
        void validationError_includesFieldErrors() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts"))
                    .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"status\":400,\"code\":\"VALIDATION_ERROR\",\"message\":\"Invalid request\","
                                    + "\"fieldErrors\":{\"title\":\"must be at most 120 characters\"}}"));

            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 5, false)))
                    .isEqualTo("CineCircle error VALIDATION_ERROR: Invalid request (title: must be at most 120 characters)");
        }

        @Test
        void invalidRating_isRejectedLocally() {
            setUp(TOKEN);
            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 11, false)))
                    .contains("rating must be a whole number from 1 to 10");
        }

        @Test
        void backendDown() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts")).andRespond(withException(new ConnectException("refused")));
            assertThat(toolError(() -> reviewTools.createReview(438631L, "t", "b", 5, false)))
                    .isEqualTo("CineCircle API is not reachable at http://localhost:8080/api");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class UpvoteReview {

        @Test
        void withoutToken_refuses() {
            setUp(null);
            assertThat(toolError(() -> reviewTools.upvoteReview("p1"))).isEqualTo("Set CINECIRCLE_TOKEN to post as your account.");
        }

        @Test
        void putsUpvote_withBearer() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts/p1/upvote")).andExpect(method(HttpMethod.PUT))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andRespond(withSuccess("{\"upvoted\":true,\"upvoteCount\":13}", MediaType.APPLICATION_JSON));

            assertThat(reviewTools.upvoteReview("p1"))
                    .isEqualTo("Upvoted. The review now has 13 upvotes.\nLink: http://localhost:5173/posts/p1");
        }

        @Test
        void notVisible_mapsNotFound() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts/nope/upvote"))
                    .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                            .body(errorJson(404, "NOT_FOUND", "Post not found")));

            assertThat(toolError(() -> reviewTools.upvoteReview("nope"))).isEqualTo("CineCircle error NOT_FOUND: Post not found");
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    @Nested
    class CommentOnReview {

        @Test
        void withoutToken_refuses() {
            setUp(null);
            assertThat(toolError(() -> reviewTools.commentOnReview("p1", "nice")))
                    .isEqualTo("Set CINECIRCLE_TOKEN to post as your account.");
        }

        @Test
        void postsComment_withBearer() {
            setUp(TOKEN);
            server.expect(requestTo(API + "/posts/p1/comments")).andExpect(method(HttpMethod.POST))
                    .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                    .andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE))
                    .andExpect(jsonPath("$.body").value("Totally agree"))
                    .andExpect(jsonPath("$.parentCommentId").doesNotExist())
                    .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                            .body("{\"id\":\"c9\",\"author\":" + user("bob", false) + ",\"body\":\"Totally agree\","
                                    + "\"deleted\":false,\"createdAt\":\"2026-11-08T10:00:00Z\",\"isMine\":true,\"replies\":[]}"));

            assertThat(reviewTools.commentOnReview("p1", "Totally agree"))
                    .isEqualTo("Comment posted (id c9).\nLink: http://localhost:5173/posts/p1");
        }

        @Test
        void blankBody_isRejectedLocally() {
            setUp(TOKEN);
            assertThat(toolError(() -> reviewTools.commentOnReview("p1", " "))).contains("body is required");
        }
    }
}

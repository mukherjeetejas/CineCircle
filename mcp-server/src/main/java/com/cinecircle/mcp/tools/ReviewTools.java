package com.cinecircle.mcp.tools;

import com.cinecircle.mcp.auth.Caller;
import com.cinecircle.mcp.client.CineCircleApiClient;
import com.cinecircle.mcp.client.dto.CreatePostRequest;
import com.cinecircle.mcp.client.dto.MovieDetail;
import com.cinecircle.mcp.client.dto.ReviewSummary;
import com.cinecircle.mcp.format.PlainTextResultConverter;
import com.cinecircle.mcp.format.ReviewFormatter;
import com.cinecircle.mcp.format.UserFormatter;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Review tools: get_movie_reviews, get_review_summary, get_popular_reviews (read) and
 * create_review, upvote_review, comment_on_review (write, need the caller's token).
 */
@Component
public class ReviewTools {

    private static final Set<String> SORTS = Set.of("top", "new");
    private static final Set<String> WINDOWS = Set.of("day", "week", "all");

    private final CineCircleApiClient api;
    private final ReviewFormatter reviews;
    private final UserFormatter users;
    private final ToolRunner runner;

    public ReviewTools(CineCircleApiClient api, ReviewFormatter reviews, UserFormatter users, ToolRunner runner) {
        this.api = api;
        this.reviews = reviews;
        this.users = users;
        this.runner = runner;
    }

    // ---- read ------------------------------------------------------------------------------------------------

    @Tool(name = "get_movie_reviews", resultConverter = PlainTextResultConverter.class, description = """
            Read CineCircle user reviews of a movie: author, rating (1-10), title, review text up to \
            300 characters (a preview; the link opens the full review), upvotes and a link. Reviews flagged as spoilers (by the author or by our AI) or still being checked \
            are hidden unless includeSpoilers=true; only set it when the user explicitly wants spoilers. \
            Needs a tmdbId from search_movies.""")
    public String getMovieReviews(
            @ToolParam(description = "TMDB movie id, from search_movies") Long tmdbId,
            @ToolParam(required = false, description = "\"top\" (most upvoted, default) or \"new\" (newest first)") String sort,
            @ToolParam(required = false, description = "Number of reviews, 1-20 (default 10)") Integer limit,
            @ToolParam(required = false, description = "Show spoiler-flagged reviews in full (default false)") Boolean includeSpoilers,
            ToolContext toolContext) {
        return runner.read("get_movie_reviews", toolContext, caller -> {
            long id = ToolArgs.tmdbId(tmdbId);
            String s = ToolArgs.oneOf("sort", sort, "top", SORTS);
            int size = ToolArgs.limit(limit);
            return reviews.movieReviews(id, s, api.moviePosts(caller, id, s, size), Boolean.TRUE.equals(includeSpoilers));
        });
    }

    @Tool(name = "get_review_summary", resultConverter = PlainTextResultConverter.class, description = """
            Get the AI-written summary of what CineCircle users think of a movie: overall sentiment, short \
            summary, pros and cons, average rating and how many reviews it is based on. Spoiler-free; built only \
            from public reviews. Needs at least 3 reviews (otherwise error NOT_ENOUGH_REVIEWS). Use this for \
            "what do people think of X" questions; get the tmdbId from search_movies.""")
    public String getReviewSummary(@ToolParam(description = "TMDB movie id, from search_movies") Long tmdbId,
            ToolContext toolContext) {
        return runner.read("get_review_summary", toolContext, caller -> {
            long id = ToolArgs.tmdbId(tmdbId);
            ReviewSummary summary = api.reviewSummary(caller, id);
            return reviews.summary(summary, movieOrNull(caller, id));
        });
    }

    /** Best-effort movie lookup for the summary heading; any failure falls back to naming the tmdbId only. */
    private MovieDetail movieOrNull(Caller caller, long tmdbId) {
        try {
            return api.movie(caller, tmdbId);
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Tool(name = "get_popular_reviews", resultConverter = PlainTextResultConverter.class, description = """
            List the most popular CineCircle reviews across all movies (hot ranking of public reviews) for a \
            time window. Each item shows movie, author, rating, title, text, upvotes and a link. Spoiler-flagged \
            reviews are always hidden here. Use this to see what the community is talking about.""")
    public String getPopularReviews(
            @ToolParam(required = false, description = "\"day\", \"week\" (default) or \"all\"") String window,
            @ToolParam(required = false, description = "Number of reviews, 1-20 (default 10)") Integer limit,
            ToolContext toolContext) {
        return runner.read("get_popular_reviews", toolContext, caller -> {
            String w = ToolArgs.oneOf("window", window, "week", WINDOWS);
            return reviews.popularReviews(w, api.popularPosts(caller, w, ToolArgs.limit(limit)));
        });
    }

    // ---- write -----------------------------------------------------------------------------------------------

    @Tool(name = "create_review", resultConverter = PlainTextResultConverter.class, description = """
            Post a movie review to CineCircle as the user's own account (requires the user's CineCircle token). \
            IMPORTANT: call search_movies first to get the real tmdbId of the movie and confirm it with the user \
            if there are several matches; never guess or invent a tmdbId. Only post text the user wrote or \
            approved. The movie poster is used as the image. Our AI checks every review for spoilers; the result \
            shows whether it will be blurred for readers. Returns the link to the new review.""")
    public String createReview(
            @ToolParam(description = "TMDB movie id, from search_movies") Long tmdbId,
            @ToolParam(description = "Review title, max 120 characters") String title,
            @ToolParam(description = "Review text, 1-5000 characters") String body,
            @ToolParam(description = "Rating from 1 (worst) to 10 (best)") Integer rating,
            @ToolParam(required = false, description = "true if the review reveals plot details (default false)") Boolean containsSpoilers,
            ToolContext toolContext) {
        return runner.write("create_review", toolContext, caller -> {
            long id = ToolArgs.tmdbId(tmdbId);
            if (rating == null || rating < 1 || rating > 10) {
                throw ToolRunner.invalid("rating must be a whole number from 1 to 10.");
            }
            String t = ToolArgs.required("title", title);
            String b = ToolArgs.required("body", body);
            CreatePostRequest req = new CreatePostRequest(id, t, b, rating, Boolean.TRUE.equals(containsSpoilers),
                    CreatePostRequest.IMAGE_TYPE_TMDB_POSTER);
            return reviews.created(api.createPost(caller, req));
        });
    }

    @Tool(name = "upvote_review", resultConverter = PlainTextResultConverter.class, description = """
            Upvote a CineCircle review as the user's own account (requires the user's CineCircle token). Safe to repeat \
            (upvoting twice counts once). postId is the id at the end of a review link .../posts/{postId}.""")
    public String upvoteReview(@ToolParam(description = "Review id (from a review link .../posts/{postId})") String postId,
            ToolContext toolContext) {
        return runner.write("upvote_review", toolContext, caller -> {
            String id = ToolArgs.required("postId", postId);
            return users.upvoted(id, api.upvote(caller, id));
        });
    }

    @Tool(name = "comment_on_review", resultConverter = PlainTextResultConverter.class, description = """
            Add a comment to a CineCircle review as the user's own account (requires the user's CineCircle token). Only \
            post text the user wrote or approved. postId is the id at the end of a review link .../posts/{postId}.""")
    public String commentOnReview(
            @ToolParam(description = "Review id (from a review link .../posts/{postId})") String postId,
            @ToolParam(description = "Comment text, max 2000 characters") String body,
            ToolContext toolContext) {
        return runner.write("comment_on_review", toolContext, caller -> {
            String id = ToolArgs.required("postId", postId);
            String b = ToolArgs.required("body", body);
            return users.commented(id, api.comment(caller, id, b));
        });
    }
}

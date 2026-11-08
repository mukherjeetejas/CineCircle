package com.cinecircle.summary;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.llm.LlmException;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.MovieService;
import com.cinecircle.post.Post;
import com.cinecircle.spoiler.AiSpoilerStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * AI review summary per movie.
 *
 * <p>Eligible posts: same movie, {@code authorIsPrivate == false}, not flagged as spoiler (neither the author's
 * {@code containsSpoilers} nor AI status SPOILER) and AI status not PENDING. Missing status (pre-v2) counts as
 * UNCHECKED and is eligible.
 *
 * <p>Cache: a stored summary is served while it is younger than 24 h, the eligible count differs from
 * {@code basedOnCount} by fewer than 5, <b>and every post it was generated from is still eligible</b>
 * ({@code sourcePostIds}); otherwise it is regenerated. One regeneration per tmdbId at a time (in-memory lock):
 * concurrent callers get the existing copy if it is still verifiable, otherwise they wait.
 *
 * <p>Fallback: if regeneration fails (LLM error, timeout, invalid JSON or no key) the last saved summary is
 * returned with {@code stale: true} - but only if all its source posts are still eligible. A summary whose
 * sources became private / spoiler-flagged / PENDING / deleted, or a legacy document without
 * {@code sourcePostIds}, is never served: 502 SUMMARY_UNAVAILABLE instead.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    public static final int MIN_REVIEWS = 3;
    public static final Duration MAX_AGE = Duration.ofHours(24);
    public static final int COUNT_DRIFT = 5;
    static final int TOP_LIMIT = 30;
    static final int NEWEST_LIMIT = 20;
    static final int MAX_BODY_CHARS = 1500;
    /** Waiting callers give up after this (the LLM call itself is bounded by 20 s). */
    static final Duration LOCK_WAIT = Duration.ofSeconds(30);

    private final MovieService movieService;
    private final MovieReviewSummaryRepository summaries;
    private final SummaryGenerator generator;
    private final MongoTemplate mongo;
    private final Clock clock;

    /** One lock per tmdbId; entries are tiny and bounded by the number of movies with >= 3 reviews. */
    private final Map<Long, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** A review sent to the model together with its post id (the id is stored, never sent). */
    record Source(String postId, SummaryGenerator.ReviewInput input) {
    }

    public ReviewSummary get(long tmdbId) {
        Movie movie = movieService.getOrFetch(tmdbId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, ErrorCode.MOVIE_NOT_FOUND, "Movie not found on TMDB", null));

        long eligible = countEligible(tmdbId);
        if (eligible < MIN_REVIEWS) {
            throw notEnough();
        }
        Optional<MovieReviewSummary> cached = summaries.findByTmdbId(tmdbId);
        boolean cachedVerified = cached.isPresent() && sourcesStillEligible(cached.get());
        if (cachedVerified && isFresh(cached.get(), eligible)) {
            return ReviewSummary.of(cached.get(), false);
        }

        ReentrantLock lock = locks.computeIfAbsent(tmdbId, k -> new ReentrantLock());
        if (!lock.tryLock()) {
            if (cachedVerified) {
                // someone else is regenerating: serve the existing (still verifiable) copy instead of waiting
                return ReviewSummary.of(cached.get(), false);
            }
            try {
                if (!lock.tryLock(LOCK_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                    throw unavailable();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw unavailable();
            }
        }
        try {
            // double-check: another caller may have regenerated while we waited
            cached = summaries.findByTmdbId(tmdbId);
            eligible = countEligible(tmdbId);
            if (eligible < MIN_REVIEWS) {
                throw notEnough();
            }
            MovieReviewSummary verified = cached.filter(this::sourcesStillEligible).orElse(null);
            if (verified != null && isFresh(verified, eligible)) {
                return ReviewSummary.of(verified, false);
            }
            return regenerate(movie, eligible, verified);
        } finally {
            lock.unlock();
        }
    }

    boolean isFresh(MovieReviewSummary s, long eligibleCount) {
        return s.getGeneratedAt() != null
                && s.getGeneratedAt().isAfter(clock.instant().minus(MAX_AGE))
                && Math.abs(eligibleCount - s.getBasedOnCount()) < COUNT_DRIFT;
    }

    /**
     * True only if the summary records its source posts and every one of them still exists and is still eligible
     * (public author, not spoiler-flagged, not PENDING, same movie). Legacy documents without ids are unverifiable.
     */
    boolean sourcesStillEligible(MovieReviewSummary s) {
        List<String> ids = s.getSourcePostIds();
        if (ids == null || ids.isEmpty() || s.getTmdbId() == null) {
            return false;
        }
        Set<String> unique = new LinkedHashSet<>(ids);
        Criteria c = eligibleCriteria(s.getTmdbId()).and("id").in(unique);
        return mongo.count(Query.query(c), Post.class) == unique.size();
    }

    /**
     * @param fallback last saved summary, already verified by {@link #sourcesStillEligible}; null when there is
     *                 none or it may not be shown
     */
    private ReviewSummary regenerate(Movie movie, long eligible, MovieReviewSummary fallback) {
        long tmdbId = movie.getTmdbId();
        List<Source> sources = loadInputs(tmdbId);
        if (sources.size() < MIN_REVIEWS) {
            throw notEnough();
        }
        List<SummaryGenerator.ReviewInput> inputs = sources.stream().map(Source::input).toList();
        SummaryGenerator.Result result;
        try {
            if (!generator.isEnabled()) {
                throw new LlmException("LLM disabled");
            }
            result = generator.generate(movie.getTitle(), movie.getReleaseYear(), inputs);
        } catch (LlmException e) {
            log.warn("Review summary for movie {} unavailable: {}", tmdbId, e.getMessage());
            if (fallback != null) {
                return ReviewSummary.of(fallback, true);
            }
            throw unavailable();
        }

        double avg = inputs.stream().mapToInt(SummaryGenerator.ReviewInput::rating).average().orElse(0);
        Instant now = clock.instant();
        Update u = new Update()
                .set("summary", result.summary())
                .set("pros", result.pros())
                .set("cons", result.cons())
                .set("sentiment", result.sentiment().name())
                .set("avgRating", Math.round(avg * 10.0) / 10.0)
                .set("basedOnCount", (int) eligible)
                .set(MovieReviewSummary.F_SOURCE_POST_IDS, sources.stream().map(Source::postId).toList())
                .set("model", generator.model())
                .set("generatedAt", now);
        Query byMovie = Query.query(Criteria.where(MovieReviewSummary.F_TMDB_ID).is(tmdbId));
        try {
            mongo.upsert(byMovie, u, MovieReviewSummary.class);
        } catch (DuplicateKeyException e) {
            // concurrent first insert from another instance: the document exists now, update it
            mongo.updateFirst(byMovie, u, MovieReviewSummary.class);
        }
        MovieReviewSummary saved = summaries.findByTmdbId(tmdbId).orElseThrow(this::unavailable);
        return ReviewSummary.of(saved, false);
    }

    // ------------------------------------------------------------------ eligibility

    /** Visibility + spoiler filter shared by count, input and source-verification queries. */
    static Criteria eligibleCriteria(long tmdbId) {
        return Criteria.where(Post.F_TMDB_ID).is(tmdbId)
                .and(Post.F_AUTHOR_IS_PRIVATE).is(false)
                .and(Post.F_CONTAINS_SPOILERS).ne(true)
                // $nin also matches documents without the field (pre-v2 = UNCHECKED)
                .and(Post.F_AI_SPOILER_STATUS).nin(AiSpoilerStatus.SPOILER.name(), AiSpoilerStatus.PENDING.name());
    }

    long countEligible(long tmdbId) {
        return mongo.count(Query.query(eligibleCriteria(tmdbId)), Post.class);
    }

    /** Top 30 by upvotes + newest 20, de-duplicated; bodies truncated to 1,500 chars; only rating/title/body sent. */
    List<Source> loadInputs(long tmdbId) {
        Query top = Query.query(eligibleCriteria(tmdbId))
                .with(Sort.by(Sort.Order.desc(Post.F_UPVOTE_COUNT), Sort.Order.desc(Post.F_CREATED_AT)))
                .limit(TOP_LIMIT);
        Query newest = Query.query(eligibleCriteria(tmdbId))
                .with(Sort.by(Sort.Order.desc(Post.F_CREATED_AT), Sort.Order.desc("_id")))
                .limit(NEWEST_LIMIT);
        for (Query q : List.of(top, newest)) {
            q.fields().include("rating", "title", "body");
        }
        Map<String, Post> byId = new LinkedHashMap<>();
        for (Post p : mongo.find(top, Post.class)) {
            byId.putIfAbsent(p.getId(), p);
        }
        for (Post p : mongo.find(newest, Post.class)) {
            byId.putIfAbsent(p.getId(), p);
        }
        List<Source> out = new ArrayList<>(byId.size());
        for (Post p : byId.values()) {
            String body = p.getBody() == null ? "" : p.getBody();
            if (body.length() > MAX_BODY_CHARS) {
                body = body.substring(0, MAX_BODY_CHARS);
            }
            out.add(new Source(p.getId(), new SummaryGenerator.ReviewInput(p.getRating(), p.getTitle(), body)));
        }
        return out;
    }

    private ApiException notEnough() {
        return new ApiException(ErrorCode.NOT_ENOUGH_REVIEWS,
                "At least " + MIN_REVIEWS + " eligible reviews are needed for a summary");
    }

    private ApiException unavailable() {
        return new ApiException(ErrorCode.SUMMARY_UNAVAILABLE, "AI summary is unavailable right now");
    }
}

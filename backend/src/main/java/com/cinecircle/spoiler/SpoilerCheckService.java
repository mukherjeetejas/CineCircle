package com.cinecircle.spoiler;

import com.cinecircle.llm.LlmCapReachedException;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.MovieRepository;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Runs the AI spoiler check for posts and persists the result with conditional, atomic updates so the
 * synchronous check on {@code POST /posts} and the background job never overwrite a finished result.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpoilerCheckService {

    public static final Duration SYNC_TIMEOUT = Duration.ofSeconds(8);
    public static final Duration JOB_TIMEOUT = Duration.ofSeconds(15);
    public static final int MAX_ATTEMPTS = 3;
    public static final int BATCH_SIZE = 20;
    public static final Duration PENDING_MIN_AGE = Duration.ofSeconds(30);

    /** Statuses a check may still write to (null = pre-v2 document = UNCHECKED). */
    private static final List<String> OPEN_STATUSES = Arrays.asList(null,
            AiSpoilerStatus.UNCHECKED.name(), AiSpoilerStatus.PENDING.name());

    private final SpoilerDetector detector;
    private final PostRepository posts;
    private final MovieRepository movies;
    private final MongoTemplate mongo;
    private final Clock clock;

    /** Status for a brand-new post: PENDING when the LLM is configured, otherwise SKIPPED. */
    public AiSpoilerStatus initialStatus() {
        return detector.isEnabled() ? AiSpoilerStatus.PENDING : AiSpoilerStatus.SKIPPED;
    }

    /**
     * Synchronous check right after a post was created (8 s timeout). Never throws: on failure the post stays
     * PENDING (attempt counted) and the job retries it later.
     *
     * @return the post with its current spoiler fields
     */
    public Post checkNow(Post post, Movie movie) {
        try {
            check(post, movie, SYNC_TIMEOUT);
        } catch (LlmCapReachedException e) {
            log.info("Spoiler check for new post {} deferred: daily AI call limit reached", post.getId());
        } catch (RuntimeException e) {
            log.warn("Spoiler check for new post {} failed unexpectedly ({})", post.getId(), e.getClass().getSimpleName());
        }
        return post;
    }

    /**
     * Background job body: up to {@value #BATCH_SIZE} posts - PENDING (attempts < 3, older than 30 s) first,
     * then UNCHECKED, each oldest first. Does nothing when the LLM is disabled.
     *
     * @return number of posts attempted
     */
    public int processBatch() {
        if (!detector.isEnabled()) {
            return 0;
        }
        List<Post> batch = nextBatch();
        if (batch.isEmpty()) {
            return 0;
        }
        Map<Long, Movie> movieById = loadMovies(batch.stream().map(Post::getTmdbId).filter(Objects::nonNull)
                .collect(Collectors.toSet()));
        int ok = 0;
        int attempted = 0;
        for (Post p : batch) {
            try {
                attempted++;
                if (check(p, movieById.get(p.getTmdbId()), JOB_TIMEOUT)) {
                    ok++;
                }
            } catch (LlmCapReachedException e) {
                // the rest of the batch would be refused too; nothing is consumed, retried after midnight UTC
                attempted--;
                log.info("Spoiler job: daily AI call limit reached, {} post(s) left for tomorrow",
                        batch.size() - attempted);
                break;
            } catch (RuntimeException e) {
                log.warn("Spoiler job: post {} failed unexpectedly ({})", p.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Spoiler job: checked {} post(s), {} succeeded", attempted, ok);
        return attempted;
    }

    List<Post> nextBatch() {
        Instant cutoff = clock.instant().minus(PENDING_MIN_AGE);
        Sort oldestFirst = Sort.by(Sort.Order.asc(Post.F_CREATED_AT), Sort.Order.asc("_id"));
        Query pending = Query.query(Criteria.where(Post.F_AI_SPOILER_STATUS).is(AiSpoilerStatus.PENDING.name())
                        .and(Post.F_AI_SPOILER_ATTEMPTS).lt(MAX_ATTEMPTS)
                        .and(Post.F_CREATED_AT).lte(cutoff))
                .with(oldestFirst).limit(BATCH_SIZE);
        List<Post> batch = new ArrayList<>(mongo.find(pending, Post.class));
        int remaining = BATCH_SIZE - batch.size();
        if (remaining > 0) {
            Query unchecked = Query.query(Criteria.where(Post.F_AI_SPOILER_STATUS)
                            .in(Arrays.asList(null, AiSpoilerStatus.UNCHECKED.name())))
                    .with(oldestFirst).limit(remaining);
            batch.addAll(mongo.find(unchecked, Post.class));
        }
        return batch;
    }

    private Map<Long, Movie> loadMovies(Collection<Long> tmdbIds) {
        if (tmdbIds.isEmpty()) {
            return Map.of();
        }
        return movies.findByTmdbIdIn(tmdbIds).stream()
                .collect(Collectors.toMap(Movie::getTmdbId, Function.identity(), (a, b) -> a));
    }

    /**
     * One attempt. On success writes CLEAN/SPOILER; on failure increments attempts and switches to SKIPPED after
     * the 3rd failure. The in-memory {@code post} is updated to mirror what was written.
     *
     * @return true when a verdict was stored
     */
    boolean check(Post post, Movie movie, Duration timeout) {
        SpoilerDetector.Input input = new SpoilerDetector.Input(
                movie != null ? movie.getTitle() : post.getMovieTitle(),
                movie != null ? movie.getReleaseYear() : post.getMovieYear(),
                movie != null ? movie.getOverview() : null,
                post.getTitle(), post.getBody());
        SpoilerVerdict verdict;
        try {
            verdict = detector.detect(input, timeout);
        } catch (LlmCapReachedException e) {
            // daily AI cap: behaves as disabled for this call - the post stays as it is (PENDING / UNCHECKED) and
            // no attempt is used up; the job picks it up again after midnight UTC
            throw e;
        } catch (RuntimeException e) {
            // LlmException (disabled/timeout/API/invalid JSON) or anything unexpected: a failed attempt
            log.info("Spoiler check attempt failed for post {}: {}", post.getId(), e.getMessage());
            recordFailure(post);
            return false;
        }
        recordVerdict(post, verdict);
        return true;
    }

    private void recordVerdict(Post post, SpoilerVerdict v) {
        Instant now = clock.instant();
        Update u = new Update()
                .set(Post.F_AI_SPOILER_STATUS, v.status().name())
                .set(Post.F_AI_SPOILER_TITLE, v.titleHidden())
                .set(Post.F_AI_SPOILER_CONFIDENCE, v.confidence())
                .set(Post.F_AI_SPOILER_CHECKED_AT, now)
                .inc(Post.F_AI_SPOILER_ATTEMPTS, 1);
        if (v.storedReason() != null) {
            u.set(Post.F_AI_SPOILER_REASON, v.storedReason());
        } else {
            u.unset(Post.F_AI_SPOILER_REASON);
        }
        long matched = mongo.updateFirst(openPost(post.getId()), u, Post.class).getMatchedCount();
        if (matched > 0) {
            post.setAiSpoilerStatus(v.status());
            post.setAiSpoilerTitle(v.titleHidden());
            post.setAiSpoilerReason(v.storedReason());
            post.setAiSpoilerConfidence(v.confidence());
            post.setAiSpoilerCheckedAt(now);
            post.setAiSpoilerAttempts(post.getAiSpoilerAttempts() + 1);
        } else {
            refresh(post);
        }
    }

    private void recordFailure(Post post) {
        mongo.updateFirst(openPost(post.getId()), new Update().inc(Post.F_AI_SPOILER_ATTEMPTS, 1), Post.class);
        Instant now = clock.instant();
        mongo.updateFirst(Query.query(openCriteria(post.getId())
                        .and(Post.F_AI_SPOILER_ATTEMPTS).gte(MAX_ATTEMPTS)),
                new Update().set(Post.F_AI_SPOILER_STATUS, AiSpoilerStatus.SKIPPED.name())
                        .set(Post.F_AI_SPOILER_CHECKED_AT, now),
                Post.class);
        refresh(post);
    }

    private void refresh(Post post) {
        posts.findById(post.getId()).ifPresent(fresh -> {
            post.setAiSpoilerStatus(fresh.getAiSpoilerStatus());
            post.setAiSpoilerTitle(fresh.isAiSpoilerTitle());
            post.setAiSpoilerReason(fresh.getAiSpoilerReason());
            post.setAiSpoilerConfidence(fresh.getAiSpoilerConfidence());
            post.setAiSpoilerAttempts(fresh.getAiSpoilerAttempts());
            post.setAiSpoilerCheckedAt(fresh.getAiSpoilerCheckedAt());
        });
    }

    private static Query openPost(String postId) {
        return Query.query(openCriteria(postId));
    }

    private static Criteria openCriteria(String postId) {
        return Criteria.where("id").is(postId).and(Post.F_AI_SPOILER_STATUS).in(OPEN_STATUSES);
    }
}

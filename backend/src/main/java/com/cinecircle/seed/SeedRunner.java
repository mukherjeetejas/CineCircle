package com.cinecircle.seed;

import com.cinecircle.comment.Comment;
import com.cinecircle.comment.CommentRepository;
import com.cinecircle.config.AppProperties;
import com.cinecircle.follow.Follow;
import com.cinecircle.follow.FollowRepository;
import com.cinecircle.follow.FollowService;
import com.cinecircle.follow.FollowStatus;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.MovieService;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.post.HotScore;
import com.cinecircle.post.ImageType;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostRepository;
import com.cinecircle.user.User;
import com.cinecircle.user.UserCounters;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import com.cinecircle.user.UserRepository;
import com.cinecircle.vote.Vote;
import com.cinecircle.vote.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Demo data on first start ({@code app.seed=true} and an empty {@code users} collection): 5 users
 * (password {@code password123}, carol is private), follows incl. one PENDING request, ~20 posts on TMDB
 * trending movies, votes and comments. Posts/votes/comments are skipped if TMDB is unreachable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeedRunner implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "password123";

    private final AppProperties props;
    private final UserRepository users;
    private final FollowRepository follows;
    private final PostRepository posts;
    private final VoteRepository votes;
    private final CommentRepository comments;
    private final MovieService movieService;
    private final FollowService followService;
    private final PasswordEncoder passwordEncoder;
    private final UserCounters userCounters;
    private final MongoTemplate mongo;

    private static final String[][] USERS = {
            // username, displayName, bio, private
            {"alice", "Alice Moreau", "Horror nerd. Will defend every A24 movie.", "false"},
            {"bob", "Bob Okafor", "Blockbusters, popcorn, no regrets.", "false"},
            {"carol", "Carol Nguyen", "Arthouse only. My reviews are for friends.", "true"},
            {"dave", "Dave Kowalski", "Sci-fi or bust.", "false"},
            {"erin", "Erin Silva", "Rewatching everything Nolan ever made.", "false"},
    };

    // follower -> followee, status
    private static final String[][] FOLLOWS = {
            {"alice", "bob", "ACCEPTED"}, {"alice", "carol", "ACCEPTED"}, {"bob", "alice", "ACCEPTED"},
            {"carol", "alice", "ACCEPTED"}, {"dave", "alice", "ACCEPTED"}, {"dave", "carol", "PENDING"},
            {"erin", "bob", "ACCEPTED"}, {"erin", "alice", "ACCEPTED"}, {"bob", "dave", "ACCEPTED"},
    };

    private static final String[] TITLES = {
            "Absolutely worth the hype", "A gorgeous mess", "Not for me, but I get it", "Instant classic",
            "Better on a second watch", "The third act though...", "Bring tissues", "Pure popcorn joy",
            "Overlong but unforgettable", "I need to talk about that ending",
    };

    private static final String[] BODIES = {
            "The cinematography alone is worth the ticket. Every frame looks like a painting and the score "
                    + "carries the quiet moments. **Performances are stellar** across the board.",
            "I went in with low expectations and walked out grinning. Pacing drags a little in the middle, "
                    + "but the payoff is huge. Would happily watch again with friends.",
            "Ambitious, messy, loud. Some of the dialogue is clunky and a subplot goes nowhere, yet I keep "
                    + "thinking about it days later. That has to count for something.",
            "Spoilers ahead: the twist reframes everything before it, and on a rewatch the clues are everywhere. "
                    + "Clever writing and a lead performance that sells every beat.",
            "Fun, fast and forgettable. Great sound design, decent action, thin characters. "
                    + "A perfect Friday night movie, not much more.",
    };

    private static final String[] COMMENTS = {
            "Totally agree, that soundtrack lives in my head now.",
            "Hard disagree on the pacing, I thought it flew by.",
            "Adding this to my watchlist!",
            "The ending wrecked me.",
            "Fair review. The middle act really does sag.",
    };

    /**
     * Seeding runs in two independent phases so a TMDB outage on first start is recovered on a later start:
     * <ol>
     *   <li>Users + follows: only when the {@code users} collection is empty.</li>
     *   <li>Posts + votes + comments: whenever the {@code posts} collection is empty AND all five demo users
     *       exist. If TMDB is unreachable (or no token is configured) this phase is skipped and retried on
     *       the next start. Set {@code APP_SEED=false} to disable seeding entirely.</li>
     * </ol>
     * All counter changes use atomic {@code $inc}/{@code $set}, never full-document saves.
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed()) {
            return;
        }
        if (users.count() == 0) {
            log.info("Seeding demo users and follows...");
            Map<String, User> created = seedUsers();
            seedFollows(created);
        }

        if (posts.count() > 0) {
            log.info("Seed: posts collection is not empty - skipping post seeding");
            return;
        }
        Map<String, User> byName = loadDemoUsers();
        if (byName.size() != USERS.length) {
            log.info("Seed: demo users not present (database has other users) - skipping post seeding");
            return;
        }
        List<Movie> movies = loadMovies();
        if (movies.isEmpty()) {
            log.warn("Seed: TMDB unreachable or not configured - skipping posts, votes and comments "
                    + "(will retry on next start while the posts collection is empty)");
            return;
        }
        List<Post> created = seedPosts(byName, movies);
        seedVotesAndComments(byName, created);
        log.info("Seed complete: {} demo users, {} posts", byName.size(), created.size());
    }

    private Map<String, User> loadDemoUsers() {
        Map<String, User> byName = new HashMap<>();
        for (String[] u : USERS) {
            users.findByUsername(u[0]).ifPresent(user -> byName.put(u[0], user));
        }
        return byName;
    }

    private Map<String, User> seedUsers() {
        Map<String, User> byName = new HashMap<>();
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        Instant base = Instant.now().minus(Duration.ofDays(30));
        for (int i = 0; i < USERS.length; i++) {
            String[] u = USERS[i];
            User user = users.insert(User.builder()
                    .username(u[0])
                    .email(u[0] + "@cinecircle.dev")
                    .passwordHash(hash)
                    .displayName(u[1])
                    .bio(u[2])
                    .privateAccount(Boolean.parseBoolean(u[3]))
                    .createdAt(base.plus(Duration.ofDays(i)))
                    .build());
            byName.put(u[0], user);
        }
        return byName;
    }

    private void seedFollows(Map<String, User> byName) {
        Instant now = Instant.now();
        for (String[] f : FOLLOWS) {
            User follower = byName.get(f[0]);
            User followee = byName.get(f[1]);
            FollowStatus status = FollowStatus.valueOf(f[2]);
            follows.insert(Follow.builder().followerId(follower.getId()).followeeId(followee.getId())
                    .status(status).createdAt(now.minus(Duration.ofDays(20))).build());
            if (status == FollowStatus.ACCEPTED) {
                userCounters.incFollowing(follower.getId(), 1);
                userCounters.incFollowers(followee.getId(), 1);
            }
        }
    }

    static final Duration MOVIE_LOAD_RETRY_DELAY = Duration.ofSeconds(2);

    /** Loads seed movies; on failure waits ~2 s and tries once more (the TMDB client itself also retries). */
    List<Movie> loadMovies() {
        List<Movie> result = loadMoviesOnce();
        if (result.isEmpty() && tmdbConfigured()) {
            log.info("Seed: no movies loaded from TMDB, retrying once in {} s", MOVIE_LOAD_RETRY_DELAY.toSeconds());
            try {
                Thread.sleep(MOVIE_LOAD_RETRY_DELAY.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return result;
            }
            result = loadMoviesOnce();
        }
        return result;
    }

    private boolean tmdbConfigured() {
        String token = props.tmdb() == null ? null : props.tmdb().token();
        return token != null && !token.isBlank();
    }

    private List<Movie> loadMoviesOnce() {
        List<TmdbModels.MovieResult> trending;
        try {
            trending = movieService.trendingResults();
        } catch (RuntimeException e) {
            log.warn("Seed: could not load trending movies from TMDB ({})", e.getMessage());
            return List.of();
        }
        List<Movie> result = new ArrayList<>();
        for (TmdbModels.MovieResult r : trending) {
            if (result.size() >= 10) {
                break;
            }
            if (r.id() == null) {
                continue;
            }
            try {
                Optional<Movie> m = movieService.getOrFetch(r.id());
                m.ifPresent(result::add);
            } catch (RuntimeException e) {
                // one flaky movie must not abort the whole seed
                log.warn("Seed: skipping movie {} ({})", r.id(), e.getMessage());
            }
        }
        return result;
    }

    private List<Post> seedPosts(Map<String, User> byName, List<Movie> movies) {
        List<Post> created = new ArrayList<>();
        Instant now = Instant.now();
        int count = 20;
        for (int i = 0; i < count; i++) {
            User author = byName.get(USERS[i % USERS.length][0]);
            Movie movie = movies.get(i % movies.size());
            boolean poster = i % 2 == 0 && movie.getPosterPath() != null;
            Instant createdAt = now.minus(Duration.ofHours(6L * i + 1));
            Post p = posts.insert(Post.builder()
                    .authorId(author.getId())
                    .authorUsername(author.getUsername())
                    .authorAvatarImageId(author.getAvatarImageId())
                    .authorIsPrivate(author.isPrivateAccount())
                    .tmdbId(movie.getTmdbId())
                    .movieTitle(movie.getTitle())
                    .movieYear(movie.getReleaseYear())
                    .moviePosterPath(movie.getPosterPath())
                    .title(TITLES[i % TITLES.length])
                    .body(BODIES[i % BODIES.length])
                    .rating(4 + (i * 3) % 7)
                    .containsSpoilers(i % BODIES.length == 3)
                    .imageType(poster ? ImageType.TMDB_POSTER : ImageType.NONE)
                    .imageRef(poster ? movie.getPosterPath() : null)
                    .hotScore(HotScore.compute(0, createdAt))
                    .createdAt(createdAt)
                    .updatedAt(createdAt)
                    .build());
            created.add(p);
            userCounters.incPosts(author.getId(), 1);
        }
        return created;
    }

    private void seedVotesAndComments(Map<String, User> byName, List<Post> created) {
        List<User> all = byName.values().stream()
                .sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
        for (int i = 0; i < created.size(); i++) {
            Post p = created.get(i);
            List<User> canSee = all.stream()
                    .filter(u -> !u.getId().equals(p.getAuthorId()))
                    .filter(u -> followService.canViewPostsOf(u.getId(), p.getAuthorId(), p.isAuthorIsPrivate()))
                    .toList();
            int upvotes = 0;
            for (int j = 0; j < canSee.size(); j++) {
                if ((i + j) % 3 != 0) {
                    votes.insert(Vote.builder().postId(p.getId()).userId(canSee.get(j).getId())
                            .createdAt(p.getCreatedAt().plus(Duration.ofMinutes(10L + j))).build());
                    upvotes++;
                }
            }
            int commentCount = 0;
            if (i % 2 == 0 && !canSee.isEmpty()) {
                User c1 = canSee.get(i % canSee.size());
                Comment top = comments.insert(Comment.builder().postId(p.getId()).authorId(c1.getId())
                        .authorUsername(c1.getUsername()).body(COMMENTS[i % COMMENTS.length])
                        .createdAt(p.getCreatedAt().plus(Duration.ofMinutes(30))).build());
                commentCount++;
                User author = byName.values().stream().filter(u -> u.getId().equals(p.getAuthorId())).findFirst()
                        .orElseThrow();
                comments.insert(Comment.builder().postId(p.getId()).authorId(author.getId())
                        .authorUsername(author.getUsername()).body("Thanks! Glad it resonated.")
                        .parentCommentId(top.getId())
                        .createdAt(p.getCreatedAt().plus(Duration.ofMinutes(45))).build());
                commentCount++;
                if (canSee.size() > 1) {
                    User c2 = canSee.get((i + 1) % canSee.size());
                    comments.insert(Comment.builder().postId(p.getId()).authorId(c2.getId())
                            .authorUsername(c2.getUsername()).body(COMMENTS[(i + 2) % COMMENTS.length])
                            .createdAt(p.getCreatedAt().plus(Duration.ofMinutes(60))).build());
                    commentCount++;
                }
            }
            // $inc counters (the server is already accepting requests while the runner executes)
            mongo.updateFirst(Query.query(Criteria.where("id").is(p.getId())),
                    new Update().inc(Post.F_UPVOTE_COUNT, upvotes).inc(Post.F_COMMENT_COUNT, commentCount)
                            .set(Post.F_HOT_SCORE, HotScore.compute(upvotes, p.getCreatedAt())),
                    Post.class);
        }
    }
}

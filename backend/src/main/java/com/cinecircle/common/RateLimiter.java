package com.cinecircle.common;

import com.cinecircle.config.LimitsProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory, per-instance rate limits (DEPLOY.md cost protection, no extra dependency). Fixed windows aligned to
 * UTC: posts and comments per user per UTC day, registrations per client IP per hour, logins per client IP per
 * minute. Exceeding a limit throws {@code 429 RATE_LIMITED} with a friendly message. A limit {@code <= 0} is off.
 *
 * <p>Posts, comments and registrations use {@code acquire} before the work and {@code release} if it fails, so a
 * rejected request (unknown movie, username taken, ...) does not use up the allowance. Every login attempt counts
 * (brute-force protection).
 */
@Component
public class RateLimiter {

    static final Duration DAY = Duration.ofDays(1);
    static final Duration HOUR = Duration.ofHours(1);
    static final Duration MINUTE = Duration.ofMinutes(1);
    /** Expired windows are swept when the map grows beyond this many keys. */
    static final int SWEEP_THRESHOLD = 10_000;

    private final LimitsProperties limits;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiter(LimitsProperties limits, Clock clock) {
        this.limits = limits;
        this.clock = clock;
    }

    /** Count and start of one fixed window (immutable; replaced atomically in the map). */
    private record Window(long index, long windowMillis, int count) {
    }

    // ------------------------------------------------------------------ domain limits

    public void acquirePost(String userId) {
        int limit = limits.postsPerDay();
        if (!tryAcquire("post", userId, limit, DAY)) {
            throw limited("You've posted " + limit + plural(limit, " review", " reviews")
                    + " today — come back tomorrow!");
        }
    }

    public void releasePost(String userId) {
        release("post", userId, limits.postsPerDay(), DAY);
    }

    public void acquireComment(String userId) {
        int limit = limits.commentsPerDay();
        if (!tryAcquire("comment", userId, limit, DAY)) {
            throw limited("You've posted " + limit + plural(limit, " comment", " comments")
                    + " today — come back tomorrow!");
        }
    }

    public void releaseComment(String userId) {
        release("comment", userId, limits.commentsPerDay(), DAY);
    }

    public void acquireRegister(String clientIp) {
        if (!tryAcquire("register", clientIp, limits.registerPerHourPerIp(), HOUR)) {
            throw limited("Too many new accounts from your network — please try again in an hour.");
        }
    }

    public void releaseRegister(String clientIp) {
        release("register", clientIp, limits.registerPerHourPerIp(), HOUR);
    }

    public void acquireLogin(String clientIp) {
        if (!tryAcquire("login", clientIp, limits.loginPerMinutePerIp(), MINUTE)) {
            throw limited("Too many login attempts — take a breather and try again in a minute.");
        }
    }

    // ------------------------------------------------------------------ core

    /** @return true if a slot was taken in the current window (always true when {@code limit <= 0}) */
    boolean tryAcquire(String bucket, String key, int limit, Duration window) {
        if (limit <= 0) {
            return true;
        }
        long windowMillis = window.toMillis();
        long index = clock.millis() / windowMillis;
        boolean[] granted = {false};
        windows.compute(bucket + '|' + (key == null ? "" : key), (k, w) -> {
            Window current = w == null || w.index() != index ? new Window(index, windowMillis, 0) : w;
            if (current.count() < limit) {
                granted[0] = true;
                return new Window(index, windowMillis, current.count() + 1);
            }
            return current;
        });
        if (windows.size() > SWEEP_THRESHOLD) {
            sweep();
        }
        return granted[0];
    }

    /** Gives back a slot taken in the current window (no-op if the window has rolled over). */
    void release(String bucket, String key, int limit, Duration window) {
        if (limit <= 0) {
            return;
        }
        long index = clock.millis() / window.toMillis();
        windows.computeIfPresent(bucket + '|' + (key == null ? "" : key), (k, w) ->
                w.index() == index && w.count() > 0 ? new Window(w.index(), w.windowMillis(), w.count() - 1) : w);
    }

    /** Drops windows that have ended. */
    void sweep() {
        long now = clock.millis();
        windows.entrySet().removeIf(e -> (e.getValue().index() + 1) * e.getValue().windowMillis() <= now);
    }

    int size() {
        return windows.size();
    }

    /** Clears all counters (tests). */
    public void reset() {
        windows.clear();
    }

    private static ApiException limited(String message) {
        return new ApiException(ErrorCode.RATE_LIMITED, message);
    }

    private static String plural(int n, String one, String many) {
        return n == 1 ? one : many;
    }
}

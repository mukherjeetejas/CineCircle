package com.cinecircle.mcp.auth;

import com.cinecircle.mcp.config.CineCircleProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Per-IP limit on tool calls ({@code MCP_RATE_LIMIT_PER_MINUTE}, fixed one-minute windows, in memory per instance).
 * Holds only IPs and counters, never tokens.
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limit;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    @Autowired
    public RateLimiter(CineCircleProperties props) {
        this(props.rateLimitPerMinute(), Clock.systemUTC());
    }

    public RateLimiter(int limitPerMinute, Clock clock) {
        this.limit = limitPerMinute;
        this.clock = clock;
    }

    /**
     * Counts one call for {@code clientIp}.
     *
     * @return 0 if allowed, else the number of seconds until the caller may try again
     */
    public long tryAcquire(String clientIp) {
        if (limit <= 0 || clientIp == null) {
            return 0;
        }
        long now = clock.millis();
        long windowStart = now - (now % WINDOW_MILLIS);
        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.start < windowStart);
        }
        Window w = windows.compute(clientIp, (ip, old) -> old == null || old.start != windowStart ? new Window(windowStart) : old);
        if (w.count.incrementAndGet() <= limit) {
            return 0;
        }
        return Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
    }

    public int limit() {
        return limit;
    }

    private static final class Window {
        final long start;
        final AtomicLong count = new AtomicLong();

        Window(long start) {
            this.start = start;
        }
    }
}

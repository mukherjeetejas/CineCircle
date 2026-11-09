package com.cinecircle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * {@code app.limits.*}: in-memory, per-instance rate limits (DEPLOY.md). A value {@code <= 0} disables that limit.
 */
@ConfigurationProperties(prefix = "app.limits")
public record LimitsProperties(
        @DefaultValue("10") int postsPerDay,
        @DefaultValue("50") int commentsPerDay,
        @DefaultValue("5") int registerPerHourPerIp,
        @DefaultValue("10") int loginPerMinutePerIp
) {
}

package com.cinecircle.spoiler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every 2 minutes: retries PENDING spoiler checks and backfills UNCHECKED (pre-v2) posts, 20 per run.
 * Scheduling is enabled by {@code SchedulingConfig} (off in tests); runs never overlap (fixed delay).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SpoilerCheckJob {

    private final SpoilerCheckService service;

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "PT2M")
    public void run() {
        try {
            service.processBatch();
        } catch (RuntimeException e) {
            log.warn("Spoiler job run failed ({})", e.getClass().getSimpleName());
        }
    }
}

package com.cinecircle.mcp.client;

import java.time.Duration;

/** Waits before the single wake-up retry. Injectable so tests don't really sleep. */
@FunctionalInterface
public interface RetrySleeper {

    RetrySleeper THREAD_SLEEP = d -> Thread.sleep(d.toMillis());

    void sleep(Duration delay) throws InterruptedException;
}

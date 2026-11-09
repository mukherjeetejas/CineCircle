package com.cinecircle.llm;

import com.cinecircle.AiIntegrationSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Daily AI call cap (DEPLOY.md): real {@link LlmClient} + {@link LlmUsageService} on the embedded MongoDB, with a
 * controllable clock and a fake Anthropic endpoint that counts calls.
 */
class LlmDailyCapTest extends AiIntegrationSupport {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-09T23:59:00Z"));
    private final AtomicInteger apiCalls = new AtomicInteger();

    @BeforeEach
    void clearUsage() {
        mongo.remove(new Query(), LlmUsage.class);
        apiCalls.set(0);
    }

    private LlmClient client(int limit) {
        LlmProperties props = new LlmProperties("sk-test", "test-model", "http://llm.test", true, limit);
        WebClient.Builder builder = WebClient.builder()
                .exchangeStrategies(ExchangeStrategies.withDefaults())
                .exchangeFunction(req -> {
                    apiCalls.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .body("{\"content\":[{\"type\":\"text\",\"text\":\"{}\"}],\"stop_reason\":\"end_turn\"}")
                            .build());
                });
        return new LlmClient(builder, props, new LlmUsageService(mongo, clock, props));
    }

    private String call(LlmClient c) {
        return c.completeJson("s", "u", 10, Duration.ofSeconds(2));
    }

    @Test
    void callRefusedAtLimitWithoutCallingTheApi() {
        LlmClient c = client(2);
        assertThat(call(c)).isEqualTo("{}");
        assertThat(call(c)).isEqualTo("{}");
        assertThatThrownBy(() -> call(c)).isInstanceOf(LlmCapReachedException.class)
                .isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> call(c)).isInstanceOf(LlmCapReachedException.class);
        assertThat(apiCalls.get()).isEqualTo(2);
        // still "enabled" (key present) - only this call behaves as disabled
        assertThat(c.isEnabled()).isTrue();
    }

    @Test
    void counterIsPerUtcDay() {
        LlmClient c = client(1);
        call(c);
        assertThatThrownBy(() -> call(c)).isInstanceOf(LlmCapReachedException.class);

        clock.set(Instant.parse("2026-11-10T00:00:01Z")); // midnight UTC passed
        assertThat(call(c)).isEqualTo("{}");
        assertThat(apiCalls.get()).isEqualTo(2);

        LlmUsage day1 = mongo.findOne(Query.query(Criteria.where("date").is("2026-11-09")), LlmUsage.class);
        LlmUsage day2 = mongo.findOne(Query.query(Criteria.where("date").is("2026-11-10")), LlmUsage.class);
        assertThat(day1).isNotNull();
        assertThat(day2.getCalls()).isEqualTo(1);
    }

    @Test
    void utcDayIsUsedRegardlessOfLocalZone() {
        // 2026-11-09T20:00 in UTC-5 is already 2026-11-10 01:00 UTC
        clock.set(Instant.parse("2026-11-10T01:00:00Z"));
        LlmUsageService usage = new LlmUsageService(mongo, clock,
                new LlmProperties("k", "m", "u", true, 5));
        assertThat(usage.today()).isEqualTo("2026-11-10");
        assertThat(usage.tryAcquire()).isTrue();
        assertThat(usage.callsToday()).isEqualTo(1);
    }

    @Test
    void sharedCounterAcrossClientsIsAtomic() throws Exception {
        LlmClient c = client(10);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
        AtomicInteger refused = new AtomicInteger();
        try {
            java.util.List<java.util.concurrent.Future<?>> fs = new java.util.ArrayList<>();
            for (int i = 0; i < 25; i++) {
                fs.add(pool.submit(() -> {
                    try {
                        call(c);
                    } catch (LlmCapReachedException e) {
                        refused.incrementAndGet();
                    }
                }));
            }
            for (var f : fs) {
                f.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(apiCalls.get()).isEqualTo(10);
        assertThat(refused.get()).isEqualTo(15);
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

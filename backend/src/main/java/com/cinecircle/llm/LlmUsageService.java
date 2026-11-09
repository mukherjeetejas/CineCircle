package com.cinecircle.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Daily AI call cap shared by all instances: before each Anthropic call {@link #tryAcquire()} atomically
 * {@code $inc}s today's {@code llm_usage} document (UTC day) and refuses when the new count exceeds
 * {@code app.llm.daily-call-limit}. Logs once per day when the cap is hit.
 */
@Slf4j
@Component
public class LlmUsageService {

    private final MongoTemplate mongo;
    private final Clock clock;
    private final int dailyLimit;
    /** UTC date for which the "cap reached" warning was last logged (once per day per instance). */
    private final AtomicReference<String> capLoggedFor = new AtomicReference<>();

    public LlmUsageService(MongoTemplate mongo, Clock clock, LlmProperties props) {
        this.mongo = mongo;
        this.clock = clock;
        this.dailyLimit = props.dailyCallLimit();
    }

    public String today() {
        return LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).toString();
    }

    public int dailyLimit() {
        return dailyLimit;
    }

    /**
     * Reserves one call for today.
     *
     * @return false when today's limit is used up (the caller must not call the API)
     */
    public boolean tryAcquire() {
        String date = today();
        long calls = increment(date);
        if (calls > dailyLimit) {
            if (!date.equals(capLoggedFor.getAndSet(date))) {
                log.warn("Daily AI call limit of {} reached for {} (UTC): AI features behave as disabled until "
                        + "midnight UTC", dailyLimit, date);
            }
            return false;
        }
        return true;
    }

    /** Calls counted for the current UTC day (refused calls included). */
    public long callsToday() {
        LlmUsage u = mongo.findOne(Query.query(Criteria.where(LlmUsage.F_DATE).is(today())), LlmUsage.class);
        return u == null ? 0 : u.getCalls();
    }

    private long increment(String date) {
        Query q = Query.query(Criteria.where(LlmUsage.F_DATE).is(date));
        Update u = new Update().inc(LlmUsage.F_CALLS, 1);
        LlmUsage doc;
        try {
            doc = mongo.findAndModify(q, u, FindAndModifyOptions.options().upsert(true).returnNew(true),
                    LlmUsage.class);
        } catch (DuplicateKeyException e) {
            // two instances created today's document at the same time: it exists now, just increment it
            doc = mongo.findAndModify(q, u, FindAndModifyOptions.options().returnNew(true), LlmUsage.class);
        }
        if (doc == null) {
            throw new LlmException("AI usage counter unavailable");
        }
        return doc.getCalls();
    }
}

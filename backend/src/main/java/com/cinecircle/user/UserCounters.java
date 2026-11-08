package com.cinecircle.user;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.util.Collection;

/** Atomic $inc updates of the denormalised user counters. */
@Component
@RequiredArgsConstructor
public class UserCounters {

    private final MongoTemplate mongo;

    public void incFollowers(String userId, long delta) {
        inc(userId, User.F_FOLLOWER_COUNT, delta);
    }

    public void incFollowing(String userId, long delta) {
        inc(userId, User.F_FOLLOWING_COUNT, delta);
    }

    public void incFollowing(Collection<String> userIds, long delta) {
        if (userIds.isEmpty()) {
            return;
        }
        mongo.updateMulti(Query.query(Criteria.where("id").in(userIds)),
                new Update().inc(User.F_FOLLOWING_COUNT, delta), User.class);
    }

    public void incPosts(String userId, long delta) {
        inc(userId, User.F_POST_COUNT, delta);
    }

    private void inc(String userId, String field, long delta) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), new Update().inc(field, delta), User.class);
    }
}

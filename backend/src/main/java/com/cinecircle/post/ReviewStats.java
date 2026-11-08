package com.cinecircle.post;

import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-movie review count and average rating, in one aggregation. Per CLAUDE.md only posts by
 * public authors (authorIsPrivate=false) are counted, everywhere these numbers appear.
 */
@Component
@RequiredArgsConstructor
public class ReviewStats {

    public record Stats(long reviewCount, Double avgRating) {
        public static final Stats EMPTY = new Stats(0, null);
    }

    private final MongoTemplate mongo;

    public Map<Long, Stats> forMovies(Collection<Long> tmdbIds) {
        Map<Long, Stats> result = new HashMap<>();
        if (tmdbIds == null || tmdbIds.isEmpty()) {
            return result;
        }
        Aggregation agg = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(Post.F_TMDB_ID).in(tmdbIds)
                        .and(Post.F_AUTHOR_IS_PRIVATE).is(false)),
                Aggregation.group(Post.F_TMDB_ID).count().as("count").avg("rating").as("avg"));
        for (Document d : mongo.aggregate(agg, Post.class, Document.class).getMappedResults()) {
            Object id = d.get("_id");
            if (id instanceof Number n) {
                Number count = (Number) d.get("count");
                Number avg = (Number) d.get("avg");
                Double rounded = avg == null ? null : Math.round(avg.doubleValue() * 10.0) / 10.0;
                result.put(n.longValue(), new Stats(count == null ? 0 : count.longValue(), rounded));
            }
        }
        return result;
    }

    public Stats forMovie(long tmdbId) {
        return forMovies(java.util.List.of(tmdbId)).getOrDefault(tmdbId, Stats.EMPTY);
    }
}

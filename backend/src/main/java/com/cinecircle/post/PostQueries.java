package com.cinecircle.post;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/** Post list queries (MongoTemplate). Callers pass {@code limit = size + 1} to detect {@code hasNext}. */
@Component
@RequiredArgsConstructor
public class PostQueries {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc(Post.F_CREATED_AT), Sort.Order.desc("_id"));
    private static final Sort HOT = Sort.by(Sort.Order.desc(Post.F_HOT_SCORE), Sort.Order.desc("_id"));
    private static final Sort TOP = Sort.by(Sort.Order.desc(Post.F_UPVOTE_COUNT), Sort.Order.desc(Post.F_CREATED_AT),
            Sort.Order.desc("_id"));

    private final MongoTemplate mongo;

    /** Posts by any of the given authors, newest first. */
    public List<Post> byAuthors(Collection<String> authorIds, long skip, int limit) {
        if (authorIds.isEmpty()) {
            return List.of();
        }
        Query q = Query.query(Criteria.where(Post.F_AUTHOR_ID).in(authorIds)).with(NEWEST).skip(skip).limit(limit);
        return mongo.find(q, Post.class);
    }

    public long countByAuthors(Collection<String> authorIds) {
        if (authorIds.isEmpty()) {
            return 0;
        }
        return mongo.count(Query.query(Criteria.where(Post.F_AUTHOR_ID).in(authorIds)), Post.class);
    }

    /** Popular posts from public authors, excluding the given authors (used for feed backfill de-dup). */
    public List<Post> popularPublicExcluding(Collection<String> excludedAuthorIds, long skip, int limit) {
        Criteria c = Criteria.where(Post.F_AUTHOR_IS_PRIVATE).is(false);
        if (!excludedAuthorIds.isEmpty()) {
            c = c.and(Post.F_AUTHOR_ID).nin(excludedAuthorIds);
        }
        return mongo.find(Query.query(c).with(HOT).skip(skip).limit(limit), Post.class);
    }

    /** Popular feed: public authors plus the viewer's own posts, optionally since {@code since}, by hot score. */
    public List<Post> popular(String viewerId, Instant since, long skip, int limit) {
        Criteria visible = viewerId == null
                ? Criteria.where(Post.F_AUTHOR_IS_PRIVATE).is(false)
                : new Criteria().orOperator(
                Criteria.where(Post.F_AUTHOR_IS_PRIVATE).is(false),
                Criteria.where(Post.F_AUTHOR_ID).is(viewerId));
        Criteria c = since == null ? visible
                : new Criteria().andOperator(visible, Criteria.where(Post.F_CREATED_AT).gte(since));
        return mongo.find(Query.query(c).with(HOT).skip(skip).limit(limit), Post.class);
    }

    /**
     * Movie page: public authors, the viewer's own posts and private authors the viewer follows (ACCEPTED).
     */
    public List<Post> byMovie(long tmdbId, String viewerId, Collection<String> followeeIds, boolean top,
                              long skip, int limit) {
        Criteria visible;
        if (viewerId == null) {
            visible = Criteria.where(Post.F_AUTHOR_IS_PRIVATE).is(false);
        } else {
            List<String> allowedPrivate = new java.util.ArrayList<>(followeeIds);
            allowedPrivate.add(viewerId);
            visible = new Criteria().orOperator(
                    Criteria.where(Post.F_AUTHOR_IS_PRIVATE).is(false),
                    Criteria.where(Post.F_AUTHOR_ID).in(allowedPrivate));
        }
        Criteria c = new Criteria().andOperator(Criteria.where(Post.F_TMDB_ID).is(tmdbId), visible);
        return mongo.find(Query.query(c).with(top ? TOP : NEWEST).skip(skip).limit(limit), Post.class);
    }

    public List<Post> byAuthor(String authorId, long skip, int limit) {
        Query q = Query.query(Criteria.where(Post.F_AUTHOR_ID).is(authorId)).with(NEWEST).skip(skip).limit(limit);
        return mongo.find(q, Post.class);
    }
}

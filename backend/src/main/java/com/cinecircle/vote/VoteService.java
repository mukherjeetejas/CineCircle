package com.cinecircle.vote;

import com.cinecircle.post.HotScore;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Idempotent upvotes. The unique (postId,userId) index decides whether a vote is new; only then is
 * upvoteCount changed with an atomic $inc and the hot score recomputed.
 */
@Service
@RequiredArgsConstructor
public class VoteService {

    private final VoteRepository votes;
    private final PostService postService;
    private final MongoTemplate mongo;

    public VoteResponse upvote(String postId, String viewerId) {
        Post post = postService.loadVisible(postId, viewerId);
        try {
            votes.insert(Vote.builder().postId(post.getId()).userId(viewerId).createdAt(Instant.now()).build());
        } catch (DuplicateKeyException alreadyVoted) {
            return new VoteResponse(true, currentCount(post.getId()));
        }
        return new VoteResponse(true, incAndRescore(post, 1));
    }

    public VoteResponse removeUpvote(String postId, String viewerId) {
        Post post = postService.loadVisible(postId, viewerId);
        Vote removed = mongo.findAndRemove(
                Query.query(Criteria.where("postId").is(post.getId()).and("userId").is(viewerId)), Vote.class);
        if (removed == null) {
            return new VoteResponse(false, currentCount(post.getId()));
        }
        return new VoteResponse(false, incAndRescore(post, -1));
    }

    private long incAndRescore(Post post, int delta) {
        Post updated = mongo.findAndModify(
                Query.query(Criteria.where("id").is(post.getId())),
                new Update().inc(Post.F_UPVOTE_COUNT, delta),
                FindAndModifyOptions.options().returnNew(true),
                Post.class);
        if (updated == null) {
            return 0; // post deleted concurrently
        }
        long count = updated.getUpvoteCount();
        // Only write the score if the count is still the one we computed it from; a concurrent vote
        // that changed the count will write its own (newer) score.
        mongo.updateFirst(
                Query.query(Criteria.where("id").is(post.getId()).and(Post.F_UPVOTE_COUNT).is(count)),
                new Update().set(Post.F_HOT_SCORE, HotScore.compute(count, updated.getCreatedAt())),
                Post.class);
        return count;
    }

    private long currentCount(String postId) {
        Post p = mongo.findById(postId, Post.class);
        return p == null ? 0 : p.getUpvoteCount();
    }
}

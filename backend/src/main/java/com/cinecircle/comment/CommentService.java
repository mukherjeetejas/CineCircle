package com.cinecircle.comment;

import com.cinecircle.comment.dto.CommentDto;
import com.cinecircle.comment.dto.CreateCommentRequest;
import com.cinecircle.common.ApiException;
import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.common.RateLimiter;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostService;
import com.cinecircle.user.User;
import com.cinecircle.user.UserDirectory;
import com.cinecircle.user.UserMapper;
import com.cinecircle.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class CommentService {

    public static final String DELETED_BODY = "[deleted]";

    private final CommentRepository comments;
    private final PostService postService;
    private final UserDirectory userDirectory;
    private final UserMapper userMapper;
    private final MongoTemplate mongo;
    private final RateLimiter rateLimiter;

    /** Top-level comments (newest first), each with its replies (oldest first). 3 queries per page. */
    public PageResponse<CommentDto> list(String postId, String viewerId, PageParams params) {
        Post post = postService.loadVisible(postId, viewerId);
        Query topQ = Query.query(Criteria.where("postId").is(post.getId()).and("parentCommentId").is(null))
                .with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("_id")))
                .skip(params.offset())
                .limit(params.size() + 1);
        PageResponse<Comment> page = PageResponse.fromOverfetch(mongo.find(topQ, Comment.class), params);
        List<Comment> top = page.items();
        if (top.isEmpty()) {
            return new PageResponse<>(List.of(), page.page(), page.size(), page.hasNext());
        }

        List<String> topIds = top.stream().map(Comment::getId).toList();
        List<Comment> replies = mongo.find(Query.query(Criteria.where("parentCommentId").in(topIds))
                .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("_id"))), Comment.class);
        Map<String, List<Comment>> repliesByParent = replies.stream()
                .collect(Collectors.groupingBy(Comment::getParentCommentId));

        Map<String, User> authors = userDirectory.byIds(
                Stream.concat(top.stream(), replies.stream()).map(Comment::getAuthorId).toList());

        List<CommentDto> items = new ArrayList<>(top.size());
        for (Comment c : top) {
            List<CommentDto> replyDtos = repliesByParent.getOrDefault(c.getId(), List.of()).stream()
                    .map(r -> toDto(r, authors, viewerId, List.of()))
                    .toList();
            items.add(toDto(c, authors, viewerId, replyDtos));
        }
        return new PageResponse<>(items, page.page(), page.size(), page.hasNext());
    }

    /** Limited to {@code app.limits.comments-per-day} per user; a rejected comment gives its slot back. */
    public CommentDto create(String postId, String viewerId, CreateCommentRequest req) {
        rateLimiter.acquireComment(viewerId);
        try {
            return doCreate(postId, viewerId, req);
        } catch (RuntimeException e) {
            rateLimiter.releaseComment(viewerId);
            throw e;
        }
    }

    private CommentDto doCreate(String postId, String viewerId, CreateCommentRequest req) {
        Post post = postService.loadVisible(postId, viewerId);
        User author = userDirectory.byId(viewerId);
        String parentId = null;
        if (req.parentCommentId() != null && !req.parentCommentId().isBlank()) {
            Comment parent = comments.findById(req.parentCommentId())
                    .filter(p -> p.getPostId().equals(post.getId()))
                    .orElseThrow(() -> ApiException.validation("parentCommentId", "Parent comment not found on this post"));
            // one level of replies: replying to a reply attaches to its top-level parent
            parentId = parent.getParentCommentId() != null ? parent.getParentCommentId() : parent.getId();
        }
        Comment saved = comments.insert(Comment.builder()
                .postId(post.getId())
                .authorId(author.getId())
                .authorUsername(author.getUsername())
                .body(req.body().strip())
                .parentCommentId(parentId)
                .deleted(false)
                .createdAt(Instant.now())
                .build());
        incCommentCount(post.getId(), 1);
        return toDto(saved, Map.of(author.getId(), author), viewerId, List.of());
    }

    /** Owner-only soft delete; idempotent for already-deleted comments. */
    public void delete(String commentId, String viewerId) {
        Comment c = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Comment not found"));
        if (!c.getAuthorId().equals(viewerId)) {
            throw ApiException.notOwner("Only the author can delete this comment");
        }
        // atomic: only the request that flips deleted=false->true decrements the counter
        long modified = mongo.updateFirst(
                Query.query(Criteria.where("id").is(c.getId()).and("deleted").is(false)),
                new Update().set("deleted", true).set("body", ""),
                Comment.class).getModifiedCount();
        if (modified > 0) {
            incCommentCount(c.getPostId(), -1);
        }
    }

    private void incCommentCount(String postId, long delta) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(postId)),
                new Update().inc(Post.F_COMMENT_COUNT, delta), Post.class);
    }

    private CommentDto toDto(Comment c, Map<String, User> authors, String viewerId, List<CommentDto> replies) {
        User u = authors.get(c.getAuthorId());
        UserSummary author = u != null ? userMapper.toSummary(u)
                : new UserSummary(c.getAuthorId(), c.getAuthorUsername(), c.getAuthorUsername(), null, false);
        return new CommentDto(c.getId(), author, c.isDeleted() ? DELETED_BODY : c.getBody(), c.isDeleted(),
                c.getCreatedAt(), viewerId != null && viewerId.equals(c.getAuthorId()), replies);
    }
}

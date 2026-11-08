package com.cinecircle.follow;

import com.cinecircle.common.ApiException;
import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.user.User;
import com.cinecircle.user.UserCounters;
import com.cinecircle.user.UserDirectory;
import com.cinecircle.user.UserMapper;
import com.cinecircle.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository follows;
    private final MongoTemplate mongo;
    private final UserDirectory userDirectory;
    private final UserCounters counters;
    private final UserMapper userMapper;

    // ---------------------------------------------------------------- queries used by other services

    /** Relation of the viewer to the target user: SELF, NONE, PENDING or ACCEPTED. */
    public FollowStatus relation(String viewerId, String targetId) {
        if (viewerId == null) {
            return FollowStatus.NONE;
        }
        if (viewerId.equals(targetId)) {
            return FollowStatus.SELF;
        }
        return follows.findByFollowerIdAndFolloweeId(viewerId, targetId)
                .map(Follow::getStatus)
                .orElse(FollowStatus.NONE);
    }

    public boolean isAcceptedFollower(String viewerId, String authorId) {
        return viewerId != null
                && follows.existsByFollowerIdAndFolloweeIdAndStatus(viewerId, authorId, FollowStatus.ACCEPTED);
    }

    /** Visibility rule for an author's posts (see {@link VisibilityPolicy}). */
    public boolean canViewPostsOf(String viewerId, String authorId, boolean authorIsPrivate) {
        if (viewerId != null && viewerId.equals(authorId) || !authorIsPrivate) {
            return true;
        }
        return VisibilityPolicy.canView(viewerId, authorId, true, isAcceptedFollower(viewerId, authorId));
    }

    /** Ids of all accounts the viewer follows with status ACCEPTED (one query). */
    public List<String> acceptedFolloweeIds(String viewerId) {
        if (viewerId == null) {
            return List.of();
        }
        return follows.findByFollowerIdAndStatus(viewerId, FollowStatus.ACCEPTED).stream()
                .map(Follow::getFolloweeId)
                .toList();
    }

    /** Ids of every account the viewer follows or has requested (ACCEPTED or PENDING), one query. */
    public List<String> allFolloweeIds(String viewerId) {
        if (viewerId == null) {
            return List.of();
        }
        Query q = Query.query(Criteria.where("followerId").is(viewerId));
        q.fields().include("followeeId");
        return mongo.find(q, Follow.class).stream().map(Follow::getFolloweeId).toList();
    }

    // ---------------------------------------------------------------- follow / unfollow

    public FollowStatus follow(String viewerId, String username) {
        User target = userDirectory.byUsername(username);
        if (target.getId().equals(viewerId)) {
            throw ApiException.validation("username", "You cannot follow yourself");
        }
        var existing = follows.findByFollowerIdAndFolloweeId(viewerId, target.getId());
        if (existing.isPresent()) {
            return existing.get().getStatus();
        }
        FollowStatus status = target.isPrivateAccount() ? FollowStatus.PENDING : FollowStatus.ACCEPTED;
        Follow follow = Follow.builder()
                .followerId(viewerId)
                .followeeId(target.getId())
                .status(status)
                .createdAt(Instant.now())
                .build();
        try {
            follows.insert(follow);
        } catch (DuplicateKeyException e) {
            // concurrent double-click: the other request created it (and updated counters)
            return follows.findByFollowerIdAndFolloweeId(viewerId, target.getId())
                    .map(Follow::getStatus).orElse(status);
        }
        if (status == FollowStatus.ACCEPTED) {
            counters.incFollowing(viewerId, 1);
            counters.incFollowers(target.getId(), 1);
        }
        return status;
    }

    /** Removes an accepted follow or cancels a pending request. Idempotent. */
    public FollowStatus unfollow(String viewerId, String username) {
        User target = userDirectory.byUsername(username);
        Follow removed = mongo.findAndRemove(
                Query.query(Criteria.where("followerId").is(viewerId).and("followeeId").is(target.getId())),
                Follow.class);
        if (removed != null && removed.getStatus() == FollowStatus.ACCEPTED) {
            counters.incFollowing(viewerId, -1);
            counters.incFollowers(target.getId(), -1);
        }
        return FollowStatus.NONE;
    }

    // ---------------------------------------------------------------- requests

    public List<UserSummary> pendingRequests(String viewerId) {
        List<Follow> pending = follows.findByFolloweeIdAndStatusOrderByCreatedAtDesc(viewerId, FollowStatus.PENDING);
        return toSummaries(pending, Follow::getFollowerId);
    }

    public void acceptRequest(String viewerId, String requesterUsername) {
        User requester = userDirectory.byUsername(requesterUsername);
        if (!promotePending(requester.getId(), viewerId)) {
            throw ApiException.notFound("No pending follow request from " + requesterUsername);
        }
    }

    public void rejectRequest(String viewerId, String requesterUsername) {
        User requester = userDirectory.byUsername(requesterUsername);
        Follow removed = mongo.findAndRemove(Query.query(Criteria.where("followerId").is(requester.getId())
                .and("followeeId").is(viewerId).and("status").is(FollowStatus.PENDING)), Follow.class);
        if (removed == null) {
            throw ApiException.notFound("No pending follow request from " + requesterUsername);
        }
    }

    /** Called when a private account becomes public: every PENDING request is accepted. */
    public int autoAcceptPending(String userId) {
        int accepted = 0;
        for (Follow f : follows.findByFolloweeIdAndStatusOrderByCreatedAtDesc(userId, FollowStatus.PENDING)) {
            if (promotePending(f.getFollowerId(), userId)) {
                accepted++;
            }
        }
        if (accepted > 0) {
            log.info("Auto-accepted {} pending follow requests for user {}", accepted, userId);
        }
        return accepted;
    }

    /** Atomically flips PENDING -> ACCEPTED and updates counters only if this call made the change. */
    private boolean promotePending(String followerId, String followeeId) {
        Follow updated = mongo.findAndModify(
                Query.query(Criteria.where("followerId").is(followerId)
                        .and("followeeId").is(followeeId).and("status").is(FollowStatus.PENDING)),
                new Update().set("status", FollowStatus.ACCEPTED),
                FindAndModifyOptions.options().returnNew(true),
                Follow.class);
        if (updated == null) {
            return false;
        }
        counters.incFollowing(followerId, 1);
        counters.incFollowers(followeeId, 1);
        return true;
    }

    // ---------------------------------------------------------------- lists

    public PageResponse<UserSummary> followers(String username, String viewerId, PageParams params) {
        User user = visibleListOwner(username, viewerId);
        return pageOf("followeeId", user.getId(), Follow::getFollowerId, params);
    }

    public PageResponse<UserSummary> following(String username, String viewerId, PageParams params) {
        User user = visibleListOwner(username, viewerId);
        return pageOf("followerId", user.getId(), Follow::getFolloweeId, params);
    }

    /** Follower/following lists follow the same rule as the account's posts (403 PRIVATE_ACCOUNT). */
    private User visibleListOwner(String username, String viewerId) {
        User user = userDirectory.byUsername(username);
        if (!canViewPostsOf(viewerId, user.getId(), user.isPrivateAccount())) {
            throw new ApiException(com.cinecircle.common.ErrorCode.PRIVATE_ACCOUNT, "This account is private");
        }
        return user;
    }

    private PageResponse<UserSummary> pageOf(String field, String userId, Function<Follow, String> other,
                                             PageParams params) {
        Query q = Query.query(Criteria.where(field).is(userId).and("status").is(FollowStatus.ACCEPTED))
                .with(Sort.by(Sort.Direction.DESC, "createdAt"))
                .skip(params.offset())
                .limit(params.size() + 1);
        PageResponse<Follow> page = PageResponse.fromOverfetch(mongo.find(q, Follow.class), params);
        return page.map(list -> toSummaries(list, other));
    }

    private List<UserSummary> toSummaries(List<Follow> list, Function<Follow, String> idFn) {
        List<String> ids = list.stream().map(idFn).toList();
        Map<String, User> byId = userDirectory.byIds(ids);
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(userMapper::toSummary).toList();
    }
}

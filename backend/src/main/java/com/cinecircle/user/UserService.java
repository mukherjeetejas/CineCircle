package com.cinecircle.user;

import com.cinecircle.follow.FollowService;
import com.cinecircle.follow.FollowStatus;
import com.cinecircle.image.ImageService;
import com.cinecircle.post.PostService;
import com.cinecircle.user.dto.UpdateMeRequest;
import com.cinecircle.user.dto.UserProfile;
import com.cinecircle.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class UserService {

    public static final int SEARCH_LIMIT = 10;

    private final UserDirectory userDirectory;
    private final UserMapper userMapper;
    private final FollowService followService;
    private final PostService postService;
    private final ImageService imageService;
    private final MongoTemplate mongo;

    public UserProfile me(String viewerId) {
        return userMapper.toProfile(userDirectory.byId(viewerId), FollowStatus.SELF, true);
    }

    public UserProfile profile(String username, String viewerId) {
        User u = userDirectory.byUsername(username);
        FollowStatus status = followService.relation(viewerId, u.getId());
        boolean canView = status == FollowStatus.SELF || !u.isPrivateAccount() || status == FollowStatus.ACCEPTED;
        return userMapper.toProfile(u, status, canView);
    }

    public UserProfile updateMe(String viewerId, UpdateMeRequest req) {
        User u = userDirectory.byId(viewerId);
        // $set/$unset only the changed fields: a full-document save would clobber counters
        // (followerCount, followingCount, postCount) that other requests update with $inc.
        Update update = new Update();
        if (req.displayName() != null) {
            update.set(User.F_DISPLAY_NAME, req.displayName().strip());
        }
        if (req.bio() != null) {
            String bio = req.bio().strip();
            if (bio.isEmpty()) {
                update.unset(User.F_BIO);
            } else {
                update.set(User.F_BIO, bio);
            }
        }
        boolean privacyChanged = req.isPrivate() != null && req.isPrivate() != u.isPrivateAccount();
        if (privacyChanged) {
            update.set(User.F_IS_PRIVATE, req.isPrivate());
        }
        if (!update.getUpdateObject().isEmpty()) {
            setFields(u.getId(), update);
        }
        if (privacyChanged) {
            postService.updateAuthorPrivacy(u.getId(), req.isPrivate());
            if (!req.isPrivate()) {
                followService.autoAcceptPending(u.getId());
            }
        }
        // reload: counters may have changed atomically (e.g. auto-accept)
        return me(viewerId);
    }

    public UserProfile updateAvatar(String viewerId, MultipartFile file) {
        User u = userDirectory.byId(viewerId);
        String newId = imageService.store(file, "file", viewerId);
        String oldId = u.getAvatarImageId();
        setFields(u.getId(), new Update().set(User.F_AVATAR_IMAGE_ID, newId));
        postService.updateAuthorAvatar(u.getId(), newId);
        if (oldId != null) {
            imageService.delete(oldId);
        }
        return me(viewerId);
    }

    private void setFields(String userId, Update update) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(userId)), update, User.class);
    }

    public static final int SUGGESTION_LIMIT = 5;

    /**
     * "Who to follow": public accounts, excluding the viewer and accounts the viewer already follows or has
     * requested (any follow status), ordered by followerCount desc, max 5. Works anonymously.
     */
    public List<UserProfile> suggestions(String viewerId) {
        List<String> excluded = new java.util.ArrayList<>();
        if (viewerId != null) {
            excluded.add(viewerId);
            excluded.addAll(followService.allFolloweeIds(viewerId));
        }
        Criteria c = Criteria.where(User.F_IS_PRIVATE).is(false);
        if (!excluded.isEmpty()) {
            c = c.and("id").nin(excluded);
        }
        Query query = Query.query(c)
                .with(Sort.by(Sort.Order.desc(User.F_FOLLOWER_COUNT), Sort.Order.asc(User.F_USERNAME)))
                .limit(SUGGESTION_LIMIT);
        return mongo.find(query, User.class).stream()
                .map(u -> userMapper.toProfile(u, FollowStatus.NONE, true))
                .toList();
    }

    /** Prefix match on username or substring match on display name, case-insensitive, max 10. */
    public List<UserSummary> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        String quoted = Pattern.quote(q.strip());
        Query query = Query.query(new Criteria().orOperator(
                        Criteria.where(User.F_USERNAME).regex("^" + quoted, "i"),
                        Criteria.where(User.F_DISPLAY_NAME).regex(quoted, "i")))
                .with(Sort.by(Sort.Order.desc(User.F_FOLLOWER_COUNT), Sort.Order.asc(User.F_USERNAME)))
                .limit(SEARCH_LIMIT);
        return mongo.find(query, User.class).stream().map(userMapper::toSummary).toList();
    }
}

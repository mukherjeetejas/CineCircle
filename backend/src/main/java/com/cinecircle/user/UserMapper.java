package com.cinecircle.user;

import com.cinecircle.common.UrlBuilder;
import com.cinecircle.follow.FollowStatus;
import com.cinecircle.user.dto.UserProfile;
import com.cinecircle.user.dto.UserSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UserMapper {

    private final UrlBuilder urls;

    public UserSummary toSummary(User u) {
        return new UserSummary(u.getId(), u.getUsername(), u.getDisplayName(),
                urls.image(u.getAvatarImageId()), u.isPrivateAccount());
    }

    public UserProfile toProfile(User u, FollowStatus followStatus, boolean canViewPosts) {
        return new UserProfile(u.getId(), u.getUsername(), u.getDisplayName(), urls.image(u.getAvatarImageId()),
                u.isPrivateAccount(), u.getBio(), u.getFollowerCount(), u.getFollowingCount(), u.getPostCount(),
                u.getCreatedAt(), followStatus, canViewPosts);
    }
}

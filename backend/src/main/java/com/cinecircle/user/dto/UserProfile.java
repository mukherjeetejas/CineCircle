package com.cinecircle.user.dto;

import com.cinecircle.follow.FollowStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** UserSummary + {bio,followerCount,followingCount,postCount,createdAt,followStatus,canViewPosts} */
public record UserProfile(
        String id,
        String username,
        String displayName,
        String avatarUrl,
        @JsonProperty("isPrivate") boolean isPrivate,
        String bio,
        long followerCount,
        long followingCount,
        long postCount,
        Instant createdAt,
        FollowStatus followStatus,
        @JsonProperty("canViewPosts") boolean canViewPosts
) {
}

package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/** UserSummary + {@code {bio,followerCount,followingCount,postCount,createdAt,followStatus,canViewPosts}} */
@JsonIgnoreProperties(ignoreUnknown = true)
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
        String followStatus,
        @JsonProperty("canViewPosts") boolean canViewPosts
) {
}

package com.cinecircle.user;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document("users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    /** Field names used in MongoTemplate queries/updates. */
    public static final String F_USERNAME = "username";
    public static final String F_DISPLAY_NAME = "displayName";
    public static final String F_BIO = "bio";
    public static final String F_AVATAR_IMAGE_ID = "avatarImageId";
    /** Java property name; stored in MongoDB as "isPrivate". */
    public static final String F_IS_PRIVATE = "privateAccount";
    public static final String F_FOLLOWER_COUNT = "followerCount";
    public static final String F_FOLLOWING_COUNT = "followingCount";
    public static final String F_POST_COUNT = "postCount";

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true)
    private String email;

    private String passwordHash;
    private String displayName;
    private String bio;
    private String avatarImageId;

    @Field("isPrivate")
    private boolean privateAccount;

    private long followerCount;
    private long followingCount;
    private long postCount;
    private Instant createdAt;
}

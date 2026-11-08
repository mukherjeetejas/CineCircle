package com.cinecircle.post;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("posts")
@CompoundIndexes({
        @CompoundIndex(name = "author_created", def = "{'authorId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "movie_created", def = "{'tmdbId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "movie_upvotes", def = "{'tmdbId': 1, 'upvoteCount': -1}"),
        @CompoundIndex(name = "private_hot", def = "{'authorIsPrivate': 1, 'hotScore': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {

    public static final String F_AUTHOR_ID = "authorId";
    public static final String F_AUTHOR_IS_PRIVATE = "authorIsPrivate";
    public static final String F_AUTHOR_AVATAR = "authorAvatarImageId";
    public static final String F_TMDB_ID = "tmdbId";
    public static final String F_UPVOTE_COUNT = "upvoteCount";
    public static final String F_COMMENT_COUNT = "commentCount";
    public static final String F_HOT_SCORE = "hotScore";
    public static final String F_CREATED_AT = "createdAt";

    @Id
    private String id;

    private String authorId;
    private String authorUsername;
    private String authorAvatarImageId;
    private boolean authorIsPrivate;

    private Long tmdbId;
    private String movieTitle;
    private Integer movieYear;
    /** Denormalised so post cards can render the movie chip poster without loading movies. */
    private String moviePosterPath;

    private String title;
    private String body;
    private int rating;
    private boolean containsSpoilers;
    private ImageType imageType;
    /** posterPath for TMDB_POSTER, GridFS id for CUSTOM, null for NONE. */
    private String imageRef;

    private long upvoteCount;
    private long commentCount;
    private double hotScore;

    private Instant createdAt;
    private Instant updatedAt;
}

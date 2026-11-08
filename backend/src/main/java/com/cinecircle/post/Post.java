package com.cinecircle.post;

import com.cinecircle.spoiler.AiSpoilerStatus;
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
        @CompoundIndex(name = "private_hot", def = "{'authorIsPrivate': 1, 'hotScore': -1}"),
        // v2: retry/backfill job of the AI spoiler detector
        @CompoundIndex(name = "ai_spoiler_created", def = "{'aiSpoilerStatus': 1, 'createdAt': 1}")
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
    public static final String F_CONTAINS_SPOILERS = "containsSpoilers";
    public static final String F_AI_SPOILER_STATUS = "aiSpoilerStatus";
    public static final String F_AI_SPOILER_TITLE = "aiSpoilerTitle";
    public static final String F_AI_SPOILER_REASON = "aiSpoilerReason";
    public static final String F_AI_SPOILER_CONFIDENCE = "aiSpoilerConfidence";
    public static final String F_AI_SPOILER_ATTEMPTS = "aiSpoilerAttempts";
    public static final String F_AI_SPOILER_CHECKED_AT = "aiSpoilerCheckedAt";

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
    /** The author's own spoiler flag (never changed by the AI). */
    private boolean containsSpoilers;
    private ImageType imageType;
    /** posterPath for TMDB_POSTER, GridFS id for CUSTOM, null for NONE. */
    private String imageRef;

    private long upvoteCount;
    private long commentCount;
    private double hotScore;

    private Instant createdAt;
    private Instant updatedAt;

    // ---- v2: AI spoiler detector. Absent on pre-v2 documents (null status == UNCHECKED).
    private AiSpoilerStatus aiSpoilerStatus;
    /** The title itself gives something away (only meaningful when status is SPOILER). */
    private boolean aiSpoilerTitle;
    /** Vague, spoiler-free reason (max 80 chars), e.g. "Reveals the ending". */
    private String aiSpoilerReason;
    private Double aiSpoilerConfidence;
    private int aiSpoilerAttempts;
    private Instant aiSpoilerCheckedAt;
}

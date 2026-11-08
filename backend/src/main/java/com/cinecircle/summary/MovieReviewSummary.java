package com.cinecircle.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

/** Cached AI review summary per movie ({@code movie_summaries}). */
@Document("movie_summaries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MovieReviewSummary {

    public static final String F_TMDB_ID = "tmdbId";
    public static final String F_SOURCE_POST_IDS = "sourcePostIds";

    @Id
    private String id;

    @Indexed(unique = true)
    private Long tmdbId;

    /** max 600 chars */
    private String summary;
    /** max 4 short strings */
    private List<String> pros;
    /** max 4 short strings */
    private List<String> cons;
    private Sentiment sentiment;
    /** Average rating of the reviews sent to the model (1 decimal). */
    private Double avgRating;
    /** Eligible-post count at generation time (used for the "+5 posts" regeneration rule). */
    private int basedOnCount;
    /**
     * Internal (never in the API): ids of the posts sent to the model. A cached copy is only served (fresh or as
     * a stale fallback) while every one of these posts still exists and is still eligible. Missing on legacy
     * documents, which are therefore treated as unverifiable.
     */
    private List<String> sourcePostIds;
    private String model;
    private Instant generatedAt;
}

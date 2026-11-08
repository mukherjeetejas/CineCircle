package com.cinecircle.common;

import com.cinecircle.comment.dto.CommentDto;
import com.cinecircle.follow.FollowStatus;
import com.cinecircle.post.dto.FeedSource;
import com.cinecircle.post.dto.PostDetail;
import com.cinecircle.post.dto.PostMovie;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.spoiler.AiSpoilerStatus;
import com.cinecircle.spoiler.SpoilerInfo;
import com.cinecircle.summary.ReviewSummary;
import com.cinecircle.summary.Sentiment;
import com.cinecircle.user.dto.UserProfile;
import com.cinecircle.user.dto.UserSummary;
import com.cinecircle.vote.VoteResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the exact JSON property names from CLAUDE.md. Jackson would otherwise strip the "is" prefix
 * from boolean accessors (isPrivate -> "private", isMine -> "mine").
 */
class JsonContractNamesTest {

    /** Same date handling as Spring Boot's auto-configured mapper (ISO-8601 strings). */
    private final ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .featuresToDisable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final UserSummary USER = new UserSummary("u1", "neo", "Neo", null, true);

    @Test
    void userSummary() {
        assertThat(fields(USER)).containsExactly("id", "username", "displayName", "avatarUrl", "isPrivate");
        assertThat(tree(USER).get("isPrivate").isBoolean()).isTrue();
    }

    @Test
    void userProfile() {
        UserProfile p = new UserProfile("u1", "neo", "Neo", null, false, "bio", 1, 2, 3, NOW,
                FollowStatus.SELF, true);
        assertThat(fields(p)).containsExactly("id", "username", "displayName", "avatarUrl", "isPrivate", "bio",
                "followerCount", "followingCount", "postCount", "createdAt", "followStatus", "canViewPosts");
        assertThat(tree(p).get("createdAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void postSummaryAndDetail() {
        SpoilerInfo spoiler = SpoilerInfo.of(true, AiSpoilerStatus.SPOILER, true, "Reveals the ending");
        PostSummary s = new PostSummary("p1", USER, new PostMovie(603, "The Matrix", 1999, null), "t", "b", 9,
                true, spoiler, null, 1, 2, true, true, NOW, FeedSource.POPULAR);
        assertThat(fields(s)).containsExactly("id", "author", "movie", "title", "bodyPreview", "rating",
                "containsSpoilers", "spoiler", "imageUrl", "upvoteCount", "commentCount", "upvotedByMe", "isMine",
                "createdAt", "source");
        assertThat(fields(tree(s).get("movie"))).containsExactly("tmdbId", "title", "releaseYear", "posterUrl");

        PostDetail d = new PostDetail("p1", USER, new PostMovie(603, "The Matrix", 1999, null), "t", "b", "body",
                9, false, SpoilerInfo.of(false, null, false, null), null, 0, 0, false, false, NOW, null);
        assertThat(fields(d)).contains("body", "containsSpoilers", "spoiler", "upvotedByMe", "isMine")
                .doesNotContain("source", "mine", "private");
    }

    @Test
    void spoilerObject() {
        JsonNode n = tree(SpoilerInfo.of(false, AiSpoilerStatus.SPOILER, true, "Reveals the ending"));
        assertThat(fields(n)).containsExactly("flagged", "source", "aiStatus", "aiReason", "titleHidden");
        assertThat(n.get("flagged").asBoolean()).isTrue();
        assertThat(n.get("source").asText()).isEqualTo("AI");
        assertThat(n.get("aiStatus").asText()).isEqualTo("SPOILER");
        assertThat(n.get("titleHidden").asBoolean()).isTrue();

        // pre-v2 post (status missing) serialises as UNCHECKED with a null reason (key still present)
        JsonNode old = tree(SpoilerInfo.of(false, null, false, null));
        assertThat(old.get("aiStatus").asText()).isEqualTo("UNCHECKED");
        assertThat(old.has("aiReason")).isTrue();
        assertThat(old.get("aiReason").isNull()).isTrue();
    }

    @Test
    void reviewSummary() {
        ReviewSummary r = new ReviewSummary(603, "Loved it", List.of("Visuals"), List.of(), Sentiment.POSITIVE, 8.5,
                12, NOW, false);
        assertThat(fields(r)).containsExactly("tmdbId", "summary", "pros", "cons", "sentiment", "avgRating",
                "basedOnCount", "generatedAt", "stale");
        assertThat(tree(r).get("generatedAt").asText()).isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void commentAndVote() {
        CommentDto c = new CommentDto("c1", USER, "[deleted]", true, NOW, false, List.of());
        assertThat(fields(c)).containsExactly("id", "author", "body", "deleted", "createdAt", "isMine", "replies");
        assertThat(fields(new VoteResponse(true, 3))).containsExactly("upvoted", "upvoteCount");
    }

    private JsonNode tree(Object o) {
        return mapper.valueToTree(o);
    }

    private List<String> fields(Object o) {
        JsonNode n = o instanceof JsonNode j ? j : tree(o);
        List<String> names = new ArrayList<>();
        n.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

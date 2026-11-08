package com.cinecircle.spoiler;

import com.cinecircle.post.Post;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** flagged = containsSpoilers (author) OR aiStatus == SPOILER; source NONE/AUTHOR/AI/BOTH. */
class SpoilerInfoTest {

    @Test
    void none() {
        SpoilerInfo s = SpoilerInfo.of(false, AiSpoilerStatus.CLEAN, false, null);
        assertThat(s.flagged()).isFalse();
        assertThat(s.source()).isEqualTo(SpoilerSource.NONE);
        assertThat(s.titleHidden()).isFalse();
    }

    @Test
    void authorOnly() {
        SpoilerInfo s = SpoilerInfo.of(true, AiSpoilerStatus.SKIPPED, false, null);
        assertThat(s.flagged()).isTrue();
        assertThat(s.source()).isEqualTo(SpoilerSource.AUTHOR);
    }

    @Test
    void aiWinsEvenWhenAuthorSaidNo() {
        SpoilerInfo s = SpoilerInfo.of(false, AiSpoilerStatus.SPOILER, true, "Reveals the ending");
        assertThat(s.flagged()).isTrue();
        assertThat(s.source()).isEqualTo(SpoilerSource.AI);
        assertThat(s.aiReason()).isEqualTo("Reveals the ending");
        assertThat(s.titleHidden()).isTrue();
    }

    @Test
    void both() {
        SpoilerInfo s = SpoilerInfo.of(true, AiSpoilerStatus.SPOILER, false, "Mentions a character's death");
        assertThat(s.flagged()).isTrue();
        assertThat(s.source()).isEqualTo(SpoilerSource.BOTH);
        assertThat(s.titleHidden()).isFalse();
    }

    @Test
    void aiCannotClearAuthorFlag() {
        SpoilerInfo s = SpoilerInfo.of(true, AiSpoilerStatus.CLEAN, false, null);
        assertThat(s.flagged()).isTrue();
        assertThat(s.source()).isEqualTo(SpoilerSource.AUTHOR);
        assertThat(s.aiStatus()).isEqualTo(AiSpoilerStatus.CLEAN);
    }

    @Test
    void titleAndReasonOnlyCountForSpoilerStatus() {
        // stale title flag / reason on a non-SPOILER status must not leak
        SpoilerInfo s = SpoilerInfo.of(false, AiSpoilerStatus.CLEAN, true, "leftover");
        assertThat(s.titleHidden()).isFalse();
        assertThat(s.aiReason()).isNull();
    }

    @Test
    void pendingIsNotFlaggedButReportsStatus() {
        SpoilerInfo s = SpoilerInfo.of(false, AiSpoilerStatus.PENDING, false, null);
        assertThat(s.flagged()).isFalse();
        assertThat(s.aiStatus()).isEqualTo(AiSpoilerStatus.PENDING);
    }

    @Test
    void preV2PostWithoutStatusIsUnchecked() {
        Post old = Post.builder().id("p").containsSpoilers(false).build();
        SpoilerInfo s = SpoilerInfo.of(old);
        assertThat(s.aiStatus()).isEqualTo(AiSpoilerStatus.UNCHECKED);
        assertThat(s.flagged()).isFalse();
        assertThat(s.source()).isEqualTo(SpoilerSource.NONE);
    }
}

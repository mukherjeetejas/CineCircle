package com.cinecircle.spoiler;

import com.cinecircle.llm.LlmClient;
import com.cinecircle.llm.LlmException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpoilerDetectorTest {

    private final LlmClient llm = mock(LlmClient.class);
    private final SpoilerDetector detector = new SpoilerDetector(llm, new ObjectMapper());

    private static final SpoilerDetector.Input INPUT = new SpoilerDetector.Input("The Matrix", 1999,
            "A hacker learns the truth.", "Mind blown", "Great movie.");

    @Test
    void highConfidenceSpoiler() {
        SpoilerVerdict v = detector.parse("{\"isSpoiler\":true,\"titleIsSpoiler\":true,\"confidence\":0.9,"
                + "\"reason\":\"Reveals the ending\"}");
        assertThat(v.status()).isEqualTo(AiSpoilerStatus.SPOILER);
        assertThat(v.titleHidden()).isTrue();
        assertThat(v.storedReason()).isEqualTo("Reveals the ending");
    }

    @Test
    void thresholdIsInclusiveAtPointSix() {
        assertThat(detector.parse("{\"isSpoiler\":true,\"titleIsSpoiler\":false,\"confidence\":0.6,\"reason\":\"x\"}")
                .status()).isEqualTo(AiSpoilerStatus.SPOILER);
    }

    @Test
    void lowConfidenceIsCleanAndTitleDoesNotCount() {
        SpoilerVerdict v = detector.parse("{\"isSpoiler\":true,\"titleIsSpoiler\":true,\"confidence\":0.59,"
                + "\"reason\":\"Maybe the ending\"}");
        assertThat(v.status()).isEqualTo(AiSpoilerStatus.CLEAN);
        assertThat(v.titleHidden()).isFalse();
        assertThat(v.storedReason()).isNull();
    }

    @Test
    void notSpoilerIsClean() {
        SpoilerVerdict v = detector.parse("{\"isSpoiler\":false,\"titleIsSpoiler\":false,\"confidence\":0.95,"
                + "\"reason\":\"\"}");
        assertThat(v.status()).isEqualTo(AiSpoilerStatus.CLEAN);
    }

    @Test
    void reasonIsCappedAt80Chars() {
        SpoilerVerdict v = detector.parse("{\"isSpoiler\":true,\"titleIsSpoiler\":false,\"confidence\":0.8,"
                + "\"reason\":\"" + "a".repeat(200) + "\"}");
        assertThat(v.reason()).hasSize(80);
    }

    @Test
    void invalidRepliesAreRejected() {
        for (String bad : new String[]{
                "not json",
                "{\"isSpoiler\":\"yes\",\"confidence\":0.9}",
                "{\"isSpoiler\":true}",
                "{\"isSpoiler\":true,\"confidence\":1.5}",
                "{\"isSpoiler\":true,\"confidence\":\"high\"}",
                "{\"isSpoiler\":true,\"confidence\":0.9,\"titleIsSpoiler\":\"no\"}",
                "{\"isSpoiler\":true,\"confidence\":0.9,\"reason\":42}"}) {
            assertThatThrownBy(() -> detector.parse(bad)).as(bad).isInstanceOf(LlmException.class);
        }
    }

    @Test
    void promptHasUntrustedInstructionAndNoUserIdentifiers() {
        when(llm.completeJson(anyString(), anyString(), eq(SpoilerDetector.MAX_TOKENS), any()))
                .thenReturn("{\"isSpoiler\":false,\"titleIsSpoiler\":false,\"confidence\":0.9,\"reason\":\"\"}");
        String longBody = "x".repeat(5000);
        detector.detect(new SpoilerDetector.Input("The Matrix", 1999, "A hacker learns the truth.",
                "Mind blown", longBody), Duration.ofSeconds(8));

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).completeJson(system.capture(), user.capture(), eq(150), eq(Duration.ofSeconds(8)));
        assertThat(system.getValue()).contains("untrusted").contains("ignore any instructions")
                .contains("at most 80 characters").contains("vague")
                .contains("isSpoiler").contains("titleIsSpoiler").contains("confidence").contains("reason");
        assertThat(user.getValue()).contains("The Matrix").contains("1999").contains("A hacker learns the truth.")
                .contains("Mind blown")
                .contains("x".repeat(SpoilerDetector.MAX_BODY_CHARS))
                .doesNotContain("x".repeat(SpoilerDetector.MAX_BODY_CHARS + 1));
        // Input carries no author/user fields at all
        assertThat(SpoilerDetector.Input.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("movieTitle", "movieYear", "overview", "reviewTitle", "reviewBody");
    }
}

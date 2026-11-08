package com.cinecircle.post;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HotScoreTest {

    private static final Instant T = Instant.ofEpochSecond(1_700_000_000L);

    @Test
    void matchesFormula() {
        double expected = Math.log10(100) + 1_700_000_000L / 45000.0;
        assertThat(HotScore.compute(100, T)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void zeroAndOneUpvotesScoreTheSame() {
        assertThat(HotScore.compute(0, T)).isEqualTo(HotScore.compute(1, T));
        assertThat(HotScore.compute(0, T)).isCloseTo(T.getEpochSecond() / 45000.0, within(1e-9));
    }

    @Test
    void moreVotesScoreHigher() {
        assertThat(HotScore.compute(10, T)).isGreaterThan(HotScore.compute(9, T));
    }

    @Test
    void newerPostsWinOverOlderWithTenTimesFewerVotesAfter12_5Hours() {
        // 45000 s = 12.5 h is worth one order of magnitude of votes
        Instant newer = T.plusSeconds(45000 + 1);
        assertThat(HotScore.compute(1, newer)).isGreaterThan(HotScore.compute(10, T));
    }
}

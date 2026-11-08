package com.cinecircle.spoiler;

import com.cinecircle.post.Post;

/**
 * {@code spoiler} object on PostSummary / PostDetail:
 * {@code {flagged, source, aiStatus, aiReason, titleHidden}}.
 *
 * <p>Rule: {@code flagged = containsSpoilers (author) OR aiStatus == SPOILER}. The AI can only add a flag, never
 * clear the author's; the author cannot clear the AI's. {@code aiReason} is only set when aiStatus is SPOILER.
 */
public record SpoilerInfo(
        boolean flagged,
        SpoilerSource source,
        AiSpoilerStatus aiStatus,
        String aiReason,
        boolean titleHidden
) {

    public static SpoilerInfo of(boolean authorFlag, AiSpoilerStatus status, boolean aiTitle, String aiReason) {
        AiSpoilerStatus s = AiSpoilerStatus.orUnchecked(status);
        boolean ai = s == AiSpoilerStatus.SPOILER;
        SpoilerSource source = authorFlag && ai ? SpoilerSource.BOTH
                : authorFlag ? SpoilerSource.AUTHOR
                : ai ? SpoilerSource.AI
                : SpoilerSource.NONE;
        return new SpoilerInfo(authorFlag || ai, source, s, ai ? aiReason : null, ai && aiTitle);
    }

    public static SpoilerInfo of(Post p) {
        return of(p.isContainsSpoilers(), p.getAiSpoilerStatus(), p.isAiSpoilerTitle(), p.getAiSpoilerReason());
    }
}

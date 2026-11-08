package com.cinecircle.spoiler;

/**
 * Validated reply of the spoiler model. {@link #status()} applies the threshold:
 * SPOILER when {@code isSpoiler && confidence >= 0.6}, else CLEAN. Title and reason only count for SPOILER.
 */
public record SpoilerVerdict(boolean isSpoiler, boolean titleIsSpoiler, double confidence, String reason) {

    public static final double THRESHOLD = 0.6;

    public AiSpoilerStatus status() {
        return isSpoiler && confidence >= THRESHOLD ? AiSpoilerStatus.SPOILER : AiSpoilerStatus.CLEAN;
    }

    public boolean titleHidden() {
        return status() == AiSpoilerStatus.SPOILER && titleIsSpoiler;
    }

    /** Reason stored on the post: only for SPOILER results. */
    public String storedReason() {
        return status() == AiSpoilerStatus.SPOILER ? reason : null;
    }
}

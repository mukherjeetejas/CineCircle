package com.cinecircle.spoiler;

/**
 * Result of the AI spoiler check stored on {@code posts.aiSpoilerStatus}.
 * <ul>
 *   <li>UNCHECKED - pre-v2 post (field missing in MongoDB); not blurred unless the author flagged it;
 *       picked up by the backfill job.</li>
 *   <li>PENDING - new post whose check has not succeeded yet (blurred by clients).</li>
 *   <li>CLEAN / SPOILER - check result.</li>
 *   <li>SKIPPED - LLM disabled at creation time, or 3 failed attempts.</li>
 * </ul>
 */
public enum AiSpoilerStatus {
    UNCHECKED, PENDING, CLEAN, SPOILER, SKIPPED;

    /** Missing field (pre-v2 documents) maps to UNCHECKED. */
    public static AiSpoilerStatus orUnchecked(AiSpoilerStatus s) {
        return s == null ? UNCHECKED : s;
    }
}

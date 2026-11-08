package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * {@code spoiler} object on PostSummary / PostDetail (FEATURES-V2.md, Feature 3):
 * {@code {flagged, source: NONE|AUTHOR|AI|BOTH, aiStatus: UNCHECKED|PENDING|CLEAN|SPOILER|SKIPPED, aiReason, titleHidden}}.
 * Enum values are kept as strings so an unknown future value never breaks deserialisation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Spoiler(boolean flagged, String source, String aiStatus, String aiReason, boolean titleHidden) {

    public static final String PENDING = "PENDING";

    public boolean pending() {
        return PENDING.equals(aiStatus);
    }

    /** Hidden unless the reader opts in: flagged (author and/or AI), or the AI check is still pending. */
    public boolean shouldHide() {
        return flagged || pending();
    }
}

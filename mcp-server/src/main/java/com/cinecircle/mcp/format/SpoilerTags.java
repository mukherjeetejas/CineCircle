package com.cinecircle.mcp.format;

import com.cinecircle.mcp.client.dto.Spoiler;

import java.util.ArrayList;
import java.util.List;

/**
 * Spoiler tags and hiding rules for tool output (FEATURES-V2.md, Feature 2 "Tool rules" + Feature 3).
 * <ul>
 *   <li>source AUTHOR → "[spoilers – flagged by author]"</li>
 *   <li>source AI → "[spoilers – flagged by AI: &lt;aiReason&gt;]"</li>
 *   <li>source BOTH → both tags</li>
 *   <li>aiStatus PENDING → "[spoiler check pending]"</li>
 * </ul>
 * Hidden (unless includeSpoilers): {@code flagged || aiStatus == PENDING}; title too when {@code titleHidden}.
 */
public final class SpoilerTags {

    public static final String AUTHOR_TAG = "[spoilers – flagged by author]";
    public static final String PENDING_TAG = "[spoiler check pending]";
    public static final String HIDDEN = "[spoilers hidden]";

    private SpoilerTags() {
    }

    public static String aiTag(String reason) {
        return Text.isBlank(reason) ? "[spoilers – flagged by AI]" : "[spoilers – flagged by AI: " + Text.clip(reason, 80) + "]";
    }

    /**
     * Tags for a review, space separated; empty string when there is nothing to tag.
     * Without a {@code spoiler} object (e.g. an older backend) the author's {@code containsSpoilers} is used.
     */
    public static String tags(Spoiler s, boolean authorFlag) {
        if (s == null) {
            return authorFlag ? AUTHOR_TAG : "";
        }
        List<String> tags = new ArrayList<>(3);
        String source = s.source() == null ? "NONE" : s.source();
        if ("AUTHOR".equals(source) || "BOTH".equals(source)) {
            tags.add(AUTHOR_TAG);
        }
        if ("AI".equals(source) || "BOTH".equals(source)) {
            tags.add(aiTag(s.aiReason()));
        }
        if (s.flagged() && tags.isEmpty()) {
            // defensive: flagged but unknown source
            tags.add("[spoilers]");
        }
        if (s.pending()) {
            tags.add(PENDING_TAG);
        }
        return String.join(" ", tags);
    }

    /** Hide when flagged or pending; without a {@code spoiler} object, when the author flagged it. */
    public static boolean hideBody(Spoiler s, boolean authorFlag, boolean includeSpoilers) {
        if (includeSpoilers) {
            return false;
        }
        return s == null ? authorFlag : s.shouldHide();
    }

    public static boolean hideTitle(Spoiler s, boolean authorFlag, boolean includeSpoilers) {
        return s != null && hideBody(s, authorFlag, includeSpoilers) && s.titleHidden();
    }
}

package com.cinecircle.follow;

/**
 * Pure visibility rule from CLAUDE.md: a post (or a profile's post list) is visible to a viewer if the
 * author is the viewer, OR the author is public, OR the viewer has an ACCEPTED follow on the author.
 */
public final class VisibilityPolicy {

    private VisibilityPolicy() {
    }

    public static boolean canView(String viewerId, String authorId, boolean authorIsPrivate,
                                  boolean viewerFollowsAuthorAccepted) {
        if (viewerId != null && viewerId.equals(authorId)) {
            return true;
        }
        if (!authorIsPrivate) {
            return true;
        }
        return viewerId != null && viewerFollowsAuthorAccepted;
    }
}

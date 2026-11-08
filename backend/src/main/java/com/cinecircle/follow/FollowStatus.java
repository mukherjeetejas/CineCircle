package com.cinecircle.follow;

/**
 * Stored follows are only {@link #PENDING} or {@link #ACCEPTED}; {@link #NONE} and {@link #SELF}
 * are used in API responses to describe the viewer's relation to a profile.
 */
public enum FollowStatus {
    NONE, PENDING, ACCEPTED, SELF
}

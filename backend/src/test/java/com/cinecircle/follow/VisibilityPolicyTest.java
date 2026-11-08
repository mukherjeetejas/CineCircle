package com.cinecircle.follow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisibilityPolicyTest {

    @Test
    void authorAlwaysSeesOwnPosts() {
        assertThat(VisibilityPolicy.canView("u1", "u1", true, false)).isTrue();
    }

    @Test
    void publicAuthorVisibleToEveryoneIncludingAnonymous() {
        assertThat(VisibilityPolicy.canView(null, "a", false, false)).isTrue();
        assertThat(VisibilityPolicy.canView("v", "a", false, false)).isTrue();
    }

    @Test
    void privateAuthorVisibleOnlyToAcceptedFollowers() {
        assertThat(VisibilityPolicy.canView("v", "a", true, true)).isTrue();
        assertThat(VisibilityPolicy.canView("v", "a", true, false)).isFalse();
    }

    @Test
    void privateAuthorHiddenFromAnonymous() {
        assertThat(VisibilityPolicy.canView(null, "a", true, true)).isFalse();
    }
}

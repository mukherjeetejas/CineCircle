package com.cinecircle.mcp.tools;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.net.ConnectException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class UserToolsTest extends ToolTestSupport {

    private static String profile(String username, boolean isPrivate, String followStatus, boolean canView) {
        return "{\"id\":\"u1\",\"username\":\"" + username + "\",\"displayName\":\"Carol C\",\"avatarUrl\":null,"
                + "\"isPrivate\":" + isPrivate + ",\"bio\":\"Horror nerd.\",\"followerCount\":1,\"followingCount\":4,"
                + "\"postCount\":7,\"createdAt\":\"2026-01-02T00:00:00Z\",\"followStatus\":\"" + followStatus + "\","
                + "\"canViewPosts\":" + canView + "}";
    }

    @Test
    void getUserProfile_publicAccount() {
        setUp(null);
        server.expect(requestTo(API + "/users/carol")).andExpect(method(HttpMethod.GET))
                .andExpect(headerDoesNotExist(HttpHeaders.AUTHORIZATION))
                .andRespond(withSuccess(profile("carol", false, "NONE", true), MediaType.APPLICATION_JSON));

        assertThat(userTools.getUserProfile("@carol")).isEqualTo("""
                @carol (Carol C) · public account
                Bio: Horror nerd.
                1 follower · 4 following · 7 reviews · joined 2026-01-02
                Your follow status: NONE
                You can see their reviews.
                Link: http://localhost:5173/u/carol""");
    }

    @Test
    void getUserProfile_privateAccount_withToken() {
        setUp(TOKEN);
        server.expect(requestTo(API + "/users/carol"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN))
                .andRespond(withSuccess(profile("carol", true, "PENDING", false), MediaType.APPLICATION_JSON));

        assertThat(userTools.getUserProfile("carol")).contains("private account").contains("Your follow status: PENDING")
                .contains("Their reviews are private").doesNotContain(TOKEN);
    }

    @Test
    void usernameIsPathEncoded() {
        setUp(null);
        server.expect(requestTo(API + "/users/a%2Fb%20c"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body(errorJson(404, "NOT_FOUND", "User not found")));

        assertThat(toolError(() -> userTools.getUserProfile("a/b c"))).isEqualTo("CineCircle error NOT_FOUND: User not found");
    }

    @Test
    void blankUsername_isRejected() {
        setUp(null);
        assertThat(toolError(() -> userTools.getUserProfile("@"))).contains("username is required");
    }

    @Test
    void backendDown() {
        setUp(null);
        server.expect(requestTo(API + "/users/carol")).andRespond(withException(new ConnectException("refused")));
        assertThat(toolError(() -> userTools.getUserProfile("carol")))
                .isEqualTo("CineCircle API is not reachable at http://localhost:8080/api");
    }
}

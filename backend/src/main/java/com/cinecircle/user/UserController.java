package com.cinecircle.user;

import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.follow.FollowResponse;
import com.cinecircle.follow.FollowService;
import com.cinecircle.post.PostService;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.security.AuthUser;
import com.cinecircle.user.dto.UpdateMeRequest;
import com.cinecircle.user.dto.UserProfile;
import com.cinecircle.user.dto.UserSummary;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final FollowService followService;
    private final PostService postService;

    // ---------------------------------------------------------------- me (auth required)

    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal AuthUser me) {
        return userService.me(me.id());
    }

    @PatchMapping("/me")
    public UserProfile updateMe(@AuthenticationPrincipal AuthUser me, @Valid @RequestBody UpdateMeRequest req) {
        return userService.updateMe(me.id(), req);
    }

    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfile avatar(@AuthenticationPrincipal AuthUser me, @RequestPart("file") MultipartFile file) {
        return userService.updateAvatar(me.id(), file);
    }

    @GetMapping("/me/follow-requests")
    public List<UserSummary> followRequests(@AuthenticationPrincipal AuthUser me) {
        return followService.pendingRequests(me.id());
    }

    @PostMapping("/me/follow-requests/{username}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        followService.acceptRequest(me.id(), username);
    }

    @PostMapping("/me/follow-requests/{username}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reject(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        followService.rejectRequest(me.id(), username);
    }

    // ---------------------------------------------------------------- public (token optional)

    @GetMapping("/search")
    public List<UserSummary> search(@RequestParam(name = "q", required = false) String q) {
        return userService.search(q);
    }

    @GetMapping("/{username}")
    public UserProfile profile(@AuthenticationPrincipal AuthUser viewer, @PathVariable String username) {
        return userService.profile(username, AuthUser.idOf(viewer));
    }

    @GetMapping("/{username}/posts")
    public PageResponse<PostSummary> posts(@AuthenticationPrincipal AuthUser viewer, @PathVariable String username,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return postService.byUser(username, AuthUser.idOf(viewer), PageParams.of(page, size));
    }

    /** Literal path: takes precedence over GET /{username}. */
    @GetMapping("/suggestions")
    public List<UserProfile> suggestions(@AuthenticationPrincipal AuthUser viewer) {
        return userService.suggestions(AuthUser.idOf(viewer));
    }

    @GetMapping("/{username}/followers")
    public PageResponse<UserSummary> followers(@AuthenticationPrincipal AuthUser viewer,
                                               @PathVariable String username,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return followService.followers(username, AuthUser.idOf(viewer), PageParams.of(page, size));
    }

    @GetMapping("/{username}/following")
    public PageResponse<UserSummary> following(@AuthenticationPrincipal AuthUser viewer,
                                               @PathVariable String username,
                                               @RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size) {
        return followService.following(username, AuthUser.idOf(viewer), PageParams.of(page, size));
    }

    // ---------------------------------------------------------------- follow (auth required)

    @PostMapping("/{username}/follow")
    public FollowResponse follow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        return new FollowResponse(followService.follow(me.id(), username));
    }

    @DeleteMapping("/{username}/follow")
    public FollowResponse unfollow(@AuthenticationPrincipal AuthUser me, @PathVariable String username) {
        return new FollowResponse(followService.unfollow(me.id(), username));
    }
}

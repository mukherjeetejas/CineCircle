package com.cinecircle.vote;

import com.cinecircle.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts/{id}/upvote")
@RequiredArgsConstructor
public class VoteController {

    private final VoteService voteService;

    @PutMapping
    public VoteResponse upvote(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return voteService.upvote(id, me.id());
    }

    @DeleteMapping
    public VoteResponse remove(@AuthenticationPrincipal AuthUser me, @PathVariable String id) {
        return voteService.removeUpvote(id, me.id());
    }
}

package com.cinecircle.feed;

import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    @GetMapping
    public PageResponse<PostSummary> feed(@AuthenticationPrincipal AuthUser me,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return feedService.feed(me.id(), PageParams.of(page, size));
    }
}

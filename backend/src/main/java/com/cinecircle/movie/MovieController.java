package com.cinecircle.movie;

import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.movie.dto.MovieDetail;
import com.cinecircle.movie.dto.MovieSummary;
import com.cinecircle.post.PostService;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;
    private final PostService postService;

    @GetMapping("/search")
    public PageResponse<MovieSummary> search(@RequestParam(name = "q", required = false) String q,
                                             @RequestParam(required = false) Integer page) {
        return movieService.search(q, page == null ? 0 : page);
    }

    @GetMapping("/trending")
    public List<MovieSummary> trending() {
        return movieService.trending();
    }

    @GetMapping("/{tmdbId}")
    public MovieDetail detail(@PathVariable long tmdbId) {
        return movieService.detail(tmdbId);
    }

    @GetMapping("/{tmdbId}/posts")
    public PageResponse<PostSummary> posts(@AuthenticationPrincipal AuthUser viewer, @PathVariable long tmdbId,
                                           @RequestParam(required = false, defaultValue = "top") String sort,
                                           @RequestParam(required = false) Integer page,
                                           @RequestParam(required = false) Integer size) {
        return postService.byMovie(tmdbId, sort, AuthUser.idOf(viewer), PageParams.of(page, size));
    }
}

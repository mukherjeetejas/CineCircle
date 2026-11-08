package com.cinecircle.summary;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public (token optional): GET /api/movies/{tmdbId}/summary. Covered by the /api/movies/** permitAll rule. */
@RestController
@RequestMapping("/api/movies")
@RequiredArgsConstructor
public class ReviewSummaryController {

    private final ReviewSummaryService service;

    @GetMapping("/{tmdbId}/summary")
    public ReviewSummary summary(@PathVariable long tmdbId) {
        return service.get(tmdbId);
    }
}

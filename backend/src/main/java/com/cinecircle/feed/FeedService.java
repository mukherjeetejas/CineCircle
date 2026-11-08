package com.cinecircle.feed;

import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.follow.FollowService;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostAssembler;
import com.cinecircle.post.PostQueries;
import com.cinecircle.post.dto.FeedSource;
import com.cinecircle.post.dto.PostSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Home feed. Conceptually the feed is one list: all posts by the viewer and their ACCEPTED followees
 * (newest first, tagged FOLLOWING), followed by popular public posts from everybody else (hot score,
 * tagged POPULAR). A page that runs out of FOLLOWING items is backfilled from the POPULAR part.
 * Excluding followed/own authors from the POPULAR part guarantees no duplicates across pages;
 * an id-set check additionally de-duplicates within a page.
 *
 * Queries per page: 1 (followees) + 1-2 (following posts [+ count only when backfilling]) +
 * 0-1 (popular) + 2 (authors + viewer votes, batched in {@link PostAssembler}).
 */
@Service
@RequiredArgsConstructor
public class FeedService {

    private final FollowService followService;
    private final PostQueries queries;
    private final PostAssembler assembler;

    public PageResponse<PostSummary> feed(String viewerId, PageParams params) {
        List<String> authorIds = new ArrayList<>(followService.acceptedFolloweeIds(viewerId));
        authorIds.add(viewerId);

        int size = params.size();
        long offset = params.offset();

        List<Post> following = queries.byAuthors(authorIds, offset, size + 1);
        if (following.size() > size) {
            // full page of FOLLOWING posts and more to come
            return page(following.subList(0, size), Map.of(), params, true, viewerId);
        }

        // Backfill: compute where this page starts inside the POPULAR section.
        long popularSkip;
        if (!following.isEmpty()) {
            popularSkip = 0; // this page contains the tail of the FOLLOWING section
        } else {
            long totalFollowing = queries.countByAuthors(authorIds);
            popularSkip = Math.max(0, offset - totalFollowing);
        }
        int needed = size - following.size();
        List<Post> popular = queries.popularPublicExcluding(authorIds, popularSkip, needed + 1);
        boolean hasNext = popular.size() > needed;

        List<Post> merged = new ArrayList<>(size);
        Map<String, FeedSource> sources = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (Post p : following) {
            if (seen.add(p.getId())) {
                merged.add(p);
                sources.put(p.getId(), FeedSource.FOLLOWING);
            }
        }
        for (Post p : popular.subList(0, Math.min(needed, popular.size()))) {
            if (seen.add(p.getId())) {
                merged.add(p);
                sources.put(p.getId(), FeedSource.POPULAR);
            }
        }
        return page(merged, sources, params, hasNext, viewerId);
    }

    private PageResponse<PostSummary> page(List<Post> posts, Map<String, FeedSource> sources, PageParams params,
                                           boolean hasNext, String viewerId) {
        List<PostSummary> items = assembler.toSummaries(posts, viewerId).stream()
                .map(s -> s.withSource(sources.getOrDefault(s.id(), FeedSource.FOLLOWING)))
                .toList();
        return new PageResponse<>(items, params.page(), params.size(), hasNext);
    }
}

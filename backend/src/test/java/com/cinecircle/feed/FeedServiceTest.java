package com.cinecircle.feed;

import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.follow.FollowService;
import com.cinecircle.post.Post;
import com.cinecircle.post.PostAssembler;
import com.cinecircle.post.PostQueries;
import com.cinecircle.post.dto.FeedSource;
import com.cinecircle.post.dto.PostMovie;
import com.cinecircle.post.dto.PostSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    private static final String VIEWER = "viewer";

    @Mock
    FollowService followService;
    @Mock
    PostQueries queries;
    @Mock
    PostAssembler assembler;
    @InjectMocks
    FeedService feedService;

    @BeforeEach
    void setUp() {
        when(followService.acceptedFolloweeIds(VIEWER)).thenReturn(List.of("f1"));
        when(assembler.toSummaries(any(), anyString())).thenAnswer(inv -> {
            List<Post> posts = inv.getArgument(0);
            return posts.stream().map(FeedServiceTest::summary).toList();
        });
    }

    @Test
    void fullFollowingPageNeedsNoBackfill() {
        when(queries.byAuthors(List.of("f1", VIEWER), 0, 4)).thenReturn(posts("f", 4));

        PageResponse<PostSummary> page = feedService.feed(VIEWER, PageParams.of(0, 3));

        assertThat(page.items()).hasSize(3).allMatch(s -> s.source() == FeedSource.FOLLOWING);
        assertThat(page.hasNext()).isTrue();
        verify(queries, never()).popularPublicExcluding(any(), anyLong(), anyInt());
    }

    @Test
    void shortFollowingPageIsBackfilledWithPopularAndDeDuplicated() {
        List<Post> following = posts("f", 2);
        // popular contains a duplicate of a following post (defensive de-dup) and three new ones
        List<Post> popular = List.of(following.get(0), post("p0", "x"), post("p1", "x"), post("p2", "x"));
        when(queries.byAuthors(List.of("f1", VIEWER), 0, 5)).thenReturn(following);
        when(queries.popularPublicExcluding(eq(List.of("f1", VIEWER)), eq(0L), eq(3))).thenReturn(popular);

        PageResponse<PostSummary> page = feedService.feed(VIEWER, PageParams.of(0, 4));

        // popular window for this page = 2 items (f0 dup, p0); the duplicate is dropped, the window is not
        // widened so later page offsets stay stable (the $nin author exclusion prevents this in practice)
        assertThat(page.items()).extracting(PostSummary::id).containsExactly("f0", "f1", "p0");
        assertThat(page.items()).extracting(PostSummary::source)
                .containsExactly(FeedSource.FOLLOWING, FeedSource.FOLLOWING, FeedSource.POPULAR);
        assertThat(page.items()).extracting(PostSummary::id).doesNotHaveDuplicates();
        assertThat(page.hasNext()).isTrue();
    }

    @Test
    void popularBackfillExcludesFollowedAndOwnAuthors() {
        when(queries.byAuthors(any(), anyLong(), anyInt())).thenReturn(List.of());
        when(queries.countByAuthors(any())).thenReturn(0L);
        when(queries.popularPublicExcluding(any(), anyLong(), anyInt())).thenReturn(List.of(post("p0", "x")));

        PageResponse<PostSummary> page = feedService.feed(VIEWER, PageParams.of(0, 20));

        verify(queries).popularPublicExcluding(List.of("f1", VIEWER), 0L, 21);
        assertThat(page.items()).singleElement().extracting(PostSummary::source).isEqualTo(FeedSource.POPULAR);
        assertThat(page.hasNext()).isFalse();
    }

    @Test
    void laterPagesContinueInsidePopularSectionWithoutRepeating() {
        // 5 following posts in total; page 2 with size 3 starts at offset 6 => popular offset 1
        when(queries.byAuthors(List.of("f1", VIEWER), 6, 4)).thenReturn(List.of());
        when(queries.countByAuthors(List.of("f1", VIEWER))).thenReturn(5L);
        when(queries.popularPublicExcluding(List.of("f1", VIEWER), 1L, 4)).thenReturn(posts("p", 2));

        PageResponse<PostSummary> page = feedService.feed(VIEWER, PageParams.of(2, 3));

        assertThat(page.items()).hasSize(2).allMatch(s -> s.source() == FeedSource.POPULAR);
        assertThat(page.hasNext()).isFalse();
    }

    private static List<Post> posts(String prefix, int n) {
        return IntStream.range(0, n).mapToObj(i -> post(prefix + i, "f1")).toList();
    }

    private static Post post(String id, String authorId) {
        return Post.builder().id(id).authorId(authorId).createdAt(Instant.now()).build();
    }

    private static PostSummary summary(Post p) {
        return new PostSummary(p.getId(), null, new PostMovie(1, "m", 2020, null), "t", "b", 5, false, null,
                0, 0, false, false, p.getCreatedAt(), null);
    }
}

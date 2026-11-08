package com.cinecircle.post;

import com.cinecircle.common.UrlBuilder;
import com.cinecircle.post.dto.PostDetail;
import com.cinecircle.post.dto.PostMovie;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.spoiler.SpoilerInfo;
import com.cinecircle.user.User;
import com.cinecircle.user.UserDirectory;
import com.cinecircle.user.UserMapper;
import com.cinecircle.user.dto.UserSummary;
import com.cinecircle.vote.Vote;
import com.cinecircle.vote.VoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps posts to DTOs without N+1 queries: one {@code $in} query for authors and one for the viewer's votes,
 * regardless of the page size.
 */
@Component
@RequiredArgsConstructor
public class PostAssembler {

    public static final int PREVIEW_LENGTH = 300;

    private final UserDirectory userDirectory;
    private final UserMapper userMapper;
    private final VoteRepository votes;
    private final UrlBuilder urls;

    public List<PostSummary> toSummaries(List<Post> posts, String viewerId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        Map<String, User> authors = userDirectory.byIds(posts.stream().map(Post::getAuthorId).toList());
        Set<String> upvoted = upvotedBy(viewerId, posts.stream().map(Post::getId).toList());
        return posts.stream()
                .map(p -> summary(p, author(p, authors), upvoted.contains(p.getId()), viewerId))
                .toList();
    }

    public PostDetail toDetail(Post p, String viewerId) {
        Map<String, User> authors = userDirectory.byIds(List.of(p.getAuthorId()));
        boolean upvoted = viewerId != null && votes.existsByPostIdAndUserId(p.getId(), viewerId);
        PostSummary s = summary(p, author(p, authors), upvoted, viewerId);
        return new PostDetail(s.id(), s.author(), s.movie(), s.title(), s.bodyPreview(), p.getBody(), s.rating(),
                s.containsSpoilers(), s.spoiler(), s.imageUrl(), s.upvoteCount(), s.commentCount(), s.upvotedByMe(),
                s.isMine(),
                s.createdAt(), null);
    }

    private Set<String> upvotedBy(String viewerId, List<String> postIds) {
        if (viewerId == null || postIds.isEmpty()) {
            return Set.of();
        }
        return votes.findByUserIdAndPostIdIn(viewerId, postIds).stream()
                .map(Vote::getPostId)
                .collect(Collectors.toSet());
    }

    private UserSummary author(Post p, Map<String, User> authors) {
        User u = authors.get(p.getAuthorId());
        if (u != null) {
            return userMapper.toSummary(u);
        }
        // fall back to denormalised fields
        return new UserSummary(p.getAuthorId(), p.getAuthorUsername(), p.getAuthorUsername(),
                urls.image(p.getAuthorAvatarImageId()), p.isAuthorIsPrivate());
    }

    private PostSummary summary(Post p, UserSummary author, boolean upvoted, String viewerId) {
        PostMovie movie = new PostMovie(p.getTmdbId() == null ? 0 : p.getTmdbId(), p.getMovieTitle(),
                p.getMovieYear(), urls.poster(p.getMoviePosterPath()));
        return new PostSummary(p.getId(), author, movie, p.getTitle(), preview(p.getBody()), p.getRating(),
                p.isContainsSpoilers(), SpoilerInfo.of(p), imageUrl(p), p.getUpvoteCount(), p.getCommentCount(), upvoted,
                viewerId != null && viewerId.equals(p.getAuthorId()), p.getCreatedAt(), null);
    }

    public String imageUrl(Post p) {
        if (p.getImageType() == null) {
            return null;
        }
        return switch (p.getImageType()) {
            case NONE -> null;
            case TMDB_POSTER -> urls.poster(p.getImageRef());
            case CUSTOM -> urls.image(p.getImageRef());
        };
    }

    public static String preview(String body) {
        if (body == null) {
            return "";
        }
        if (body.length() <= PREVIEW_LENGTH) {
            return body;
        }
        return body.substring(0, PREVIEW_LENGTH - 1).stripTrailing() + "…";
    }
}

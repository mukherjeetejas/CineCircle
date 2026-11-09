package com.cinecircle.post;

import com.cinecircle.comment.CommentRepository;
import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.common.PageParams;
import com.cinecircle.common.PageResponse;
import com.cinecircle.common.RateLimiter;
import com.cinecircle.follow.FollowService;
import com.cinecircle.image.ImageService;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.MovieService;
import com.cinecircle.post.dto.CreatePostRequest;
import com.cinecircle.post.dto.PostDetail;
import com.cinecircle.post.dto.PostSummary;
import com.cinecircle.spoiler.AiSpoilerStatus;
import com.cinecircle.spoiler.SpoilerCheckService;
import com.cinecircle.user.User;
import com.cinecircle.user.UserCounters;
import com.cinecircle.user.UserDirectory;
import com.cinecircle.vote.VoteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository posts;
    private final PostQueries queries;
    private final PostAssembler assembler;
    private final MovieService movieService;
    private final FollowService followService;
    private final ImageService imageService;
    private final VoteRepository votes;
    private final CommentRepository comments;
    private final UserDirectory userDirectory;
    private final UserCounters userCounters;
    private final MongoTemplate mongo;
    private final SpoilerCheckService spoilerChecks;
    private final RateLimiter rateLimiter;
    private final Clock clock = Clock.systemUTC();

    // ------------------------------------------------------------------ create

    /** Limited to {@code app.limits.posts-per-day} per user; a rejected create gives its slot back. */
    public PostDetail create(String viewerId, CreatePostRequest req, MultipartFile image) {
        rateLimiter.acquirePost(viewerId);
        try {
            return doCreate(viewerId, req, image);
        } catch (RuntimeException e) {
            rateLimiter.releasePost(viewerId);
            throw e;
        }
    }

    private PostDetail doCreate(String viewerId, CreatePostRequest req, MultipartFile image) {
        User author = userDirectory.byId(viewerId);
        Movie movie = movieService.getOrFetch(req.tmdbId())
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.MOVIE_NOT_FOUND,
                        "Movie " + req.tmdbId() + " does not exist on TMDB",
                        java.util.Map.of("tmdbId", "Unknown TMDB movie id")));

        String imageRef = switch (req.imageType()) {
            case NONE -> null;
            case TMDB_POSTER -> {
                if (movie.getPosterPath() == null || movie.getPosterPath().isBlank()) {
                    throw ApiException.validation("imageType", "This movie has no TMDB poster");
                }
                yield movie.getPosterPath();
            }
            case CUSTOM -> {
                if (image == null || image.isEmpty()) {
                    throw ApiException.validation("image", "An image is required when imageType is CUSTOM");
                }
                yield imageService.store(image, "image", viewerId);
            }
        };

        Instant now = clock.instant();
        Post post = Post.builder()
                .authorId(author.getId())
                .authorUsername(author.getUsername())
                .authorAvatarImageId(author.getAvatarImageId())
                .authorIsPrivate(author.isPrivateAccount())
                .tmdbId(movie.getTmdbId())
                .movieTitle(movie.getTitle())
                .movieYear(movie.getReleaseYear())
                .moviePosterPath(movie.getPosterPath())
                .title(req.title())
                .body(req.body())
                .rating(req.rating())
                .containsSpoilers(Boolean.TRUE.equals(req.containsSpoilers()))
                .imageType(req.imageType())
                .imageRef(imageRef)
                .upvoteCount(0)
                .commentCount(0)
                .hotScore(HotScore.compute(0, now))
                .aiSpoilerStatus(spoilerChecks.initialStatus())
                .aiSpoilerAttempts(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        try {
            post = posts.insert(post);
        } catch (RuntimeException e) {
            if (req.imageType() == ImageType.CUSTOM) {
                imageService.delete(imageRef);
            }
            throw e;
        }
        userCounters.incPosts(author.getId(), 1);
        if (post.getAiSpoilerStatus() == AiSpoilerStatus.PENDING) {
            // synchronous AI spoiler check (8 s); never fails creation - on error the post stays PENDING
            post = spoilerChecks.checkNow(post, movie);
        }
        return assembler.toDetail(post, viewerId);
    }

    // ------------------------------------------------------------------ read

    /** Loads a post the viewer may see; invisible posts are reported as 404 (no existence leak). */
    public Post loadVisible(String postId, String viewerId) {
        Post post = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Post not found"));
        if (!followService.canViewPostsOf(viewerId, post.getAuthorId(), post.isAuthorIsPrivate())) {
            throw ApiException.notFound("Post not found");
        }
        return post;
    }

    public PostDetail get(String postId, String viewerId) {
        return assembler.toDetail(loadVisible(postId, viewerId), viewerId);
    }

    public PageResponse<PostSummary> popular(String window, String viewerId, PageParams params) {
        Instant since = switch (window == null ? "all" : window.toLowerCase(Locale.ROOT)) {
            case "day" -> clock.instant().minus(Duration.ofDays(1));
            case "week" -> clock.instant().minus(Duration.ofDays(7));
            case "all" -> null;
            default -> throw ApiException.validation("window", "must be one of day, week, all");
        };
        List<Post> found = queries.popular(viewerId, since, params.offset(), params.size() + 1);
        return PageResponse.fromOverfetch(found, params).map(list -> assembler.toSummaries(list, viewerId));
    }

    public PageResponse<PostSummary> byUser(String username, String viewerId, PageParams params) {
        User author = userDirectory.byUsername(username);
        if (!followService.canViewPostsOf(viewerId, author.getId(), author.isPrivateAccount())) {
            throw new ApiException(ErrorCode.PRIVATE_ACCOUNT, "This account is private");
        }
        List<Post> found = queries.byAuthor(author.getId(), params.offset(), params.size() + 1);
        return PageResponse.fromOverfetch(found, params).map(list -> assembler.toSummaries(list, viewerId));
    }

    public PageResponse<PostSummary> byMovie(long tmdbId, String sort, String viewerId, PageParams params) {
        boolean top = switch (sort == null ? "top" : sort.toLowerCase(Locale.ROOT)) {
            case "top" -> true;
            case "new" -> false;
            default -> throw ApiException.validation("sort", "must be one of top, new");
        };
        List<String> followees = followService.acceptedFolloweeIds(viewerId);
        List<Post> found = queries.byMovie(tmdbId, viewerId, followees, top, params.offset(), params.size() + 1);
        return PageResponse.fromOverfetch(found, params).map(list -> assembler.toSummaries(list, viewerId));
    }

    // ------------------------------------------------------------------ delete

    public void delete(String postId, String viewerId) {
        Post post = loadVisible(postId, viewerId);
        if (!post.getAuthorId().equals(viewerId)) {
            throw ApiException.notOwner("Only the author can delete this post");
        }
        votes.deleteByPostId(post.getId());
        comments.deleteByPostId(post.getId());
        if (post.getImageType() == ImageType.CUSTOM) {
            imageService.delete(post.getImageRef());
        }
        long removed = mongo.remove(Query.query(Criteria.where("id").is(post.getId())), Post.class).getDeletedCount();
        if (removed > 0) {
            userCounters.incPosts(post.getAuthorId(), -1);
        }
    }

    // ------------------------------------------------------------------ denormalisation maintenance

    public void updateAuthorPrivacy(String authorId, boolean isPrivate) {
        mongo.updateMulti(Query.query(Criteria.where(Post.F_AUTHOR_ID).is(authorId)),
                new Update().set(Post.F_AUTHOR_IS_PRIVATE, isPrivate), Post.class);
    }

    public void updateAuthorAvatar(String authorId, String avatarImageId) {
        mongo.updateMulti(Query.query(Criteria.where(Post.F_AUTHOR_ID).is(authorId)),
                new Update().set(Post.F_AUTHOR_AVATAR, avatarImageId), Post.class);
    }
}

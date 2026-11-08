package com.cinecircle.post;

import com.cinecircle.comment.Comment;
import com.cinecircle.comment.CommentRepository;
import com.cinecircle.comment.CommentService;
import com.cinecircle.common.ApiException;
import com.cinecircle.common.ErrorCode;
import com.cinecircle.follow.FollowService;
import com.cinecircle.image.ImageService;
import com.cinecircle.movie.MovieService;
import com.cinecircle.user.UserCounters;
import com.cinecircle.user.UserDirectory;
import com.cinecircle.user.UserMapper;
import com.cinecircle.vote.VoteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OwnershipTest {

    @Mock PostRepository posts;
    @Mock PostQueries queries;
    @Mock PostAssembler assembler;
    @Mock MovieService movieService;
    @Mock FollowService followService;
    @Mock ImageService imageService;
    @Mock VoteRepository votes;
    @Mock CommentRepository comments;
    @Mock UserDirectory userDirectory;
    @Mock UserCounters userCounters;
    @Mock MongoTemplate mongo;
    @Mock UserMapper userMapper;

    private PostService postService() {
        return new PostService(posts, queries, assembler, movieService, followService, imageService, votes,
                comments, userDirectory, userCounters, mongo);
    }

    @Test
    void nonOwnerCannotDeletePostAndNothingIsRemoved() {
        Post post = Post.builder().id("p1").authorId("author").authorIsPrivate(false)
                .imageType(ImageType.CUSTOM).imageRef("img").createdAt(Instant.now()).build();
        when(posts.findById("p1")).thenReturn(Optional.of(post));
        when(followService.canViewPostsOf("intruder", "author", false)).thenReturn(true);

        assertThatThrownBy(() -> postService().delete("p1", "intruder"))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> {
                    ApiException ex = (ApiException) e;
                    org.assertj.core.api.Assertions.assertThat(ex.getCode()).isEqualTo(ErrorCode.NOT_OWNER);
                    org.assertj.core.api.Assertions.assertThat(ex.getStatus().value()).isEqualTo(403);
                });
        verify(votes, never()).deleteByPostId(anyString());
        verify(comments, never()).deleteByPostId(anyString());
        verify(imageService, never()).delete(anyString());
        verifyNoInteractions(userCounters);
    }

    @Test
    void invisiblePostIsNotFoundEvenForDeleteAttempt() {
        Post post = Post.builder().id("p1").authorId("author").authorIsPrivate(true).build();
        when(posts.findById("p1")).thenReturn(Optional.of(post));
        when(followService.canViewPostsOf("stranger", "author", true)).thenReturn(false);

        assertThatThrownBy(() -> postService().delete("p1", "stranger"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    void nonOwnerCannotDeleteComment() {
        CommentService commentService = new CommentService(comments, postService(), userDirectory, userMapper, mongo);
        when(comments.findById("c1")).thenReturn(Optional.of(
                Comment.builder().id("c1").postId("p1").authorId("author").body("hi").build()));

        assertThatThrownBy(() -> commentService.delete("c1", "intruder"))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getCode()).isEqualTo(ErrorCode.NOT_OWNER);
        verify(mongo, never()).updateFirst(any(), any(), any(Class.class));
    }
}

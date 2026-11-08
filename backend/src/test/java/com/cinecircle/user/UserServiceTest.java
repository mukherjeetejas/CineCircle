package com.cinecircle.user;

import com.cinecircle.follow.FollowService;
import com.cinecircle.image.ImageService;
import com.cinecircle.post.PostService;
import com.cinecircle.user.dto.UpdateMeRequest;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Profile edits must $set only changed fields so concurrent $inc counter updates are never overwritten. */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock UserDirectory userDirectory;
    @Mock UserMapper userMapper;
    @Mock FollowService followService;
    @Mock PostService postService;
    @Mock ImageService imageService;
    @Mock MongoTemplate mongo;

    private UserService service() {
        return new UserService(userDirectory, userMapper, followService, postService, imageService, mongo);
    }

    private User user() {
        return User.builder().id("u1").username("neo").displayName("Neo").privateAccount(false)
                .followerCount(5).followingCount(6).postCount(7).build();
    }

    @Test
    void updateMeSetsOnlyChangedFields() {
        when(userDirectory.byId("u1")).thenReturn(user());

        service().updateMe("u1", new UpdateMeRequest("The One", "", true));

        Document update = captureUpdate();
        assertThat(update.keySet()).containsExactlyInAnyOrder("$set", "$unset");
        assertThat(((Document) update.get("$set")).keySet()).containsExactlyInAnyOrder("displayName", "privateAccount");
        assertThat(((Document) update.get("$unset")).keySet()).containsExactly("bio");
        assertThat(update.toJson()).doesNotContain("Count");
        verify(postService).updateAuthorPrivacy("u1", true);
        verify(followService, never()).autoAcceptPending(any());
    }

    @Test
    void goingPublicAutoAcceptsPending() {
        User u = user();
        u.setPrivateAccount(true);
        when(userDirectory.byId("u1")).thenReturn(u);

        service().updateMe("u1", new UpdateMeRequest(null, null, false));

        assertThat(((Document) captureUpdate().get("$set")).keySet()).containsExactly("privateAccount");
        verify(postService).updateAuthorPrivacy("u1", false);
        verify(followService).autoAcceptPending("u1");
    }

    @Test
    void avatarUpdateSetsOnlyAvatarId() {
        when(userDirectory.byId("u1")).thenReturn(user());
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[]{1});
        when(imageService.store(file, "file", "u1")).thenReturn("img1");

        service().updateAvatar("u1", file);

        Document update = captureUpdate();
        assertThat(update.keySet()).containsExactly("$set");
        assertThat(((Document) update.get("$set"))).containsOnlyKeys("avatarImageId");
        verify(postService).updateAuthorAvatar("u1", "img1");
    }

    private Document captureUpdate() {
        ArgumentCaptor<Update> captor = ArgumentCaptor.forClass(Update.class);
        verify(mongo).updateFirst(any(Query.class), captor.capture(), eq(User.class));
        return captor.getValue().getUpdateObject();
    }
}

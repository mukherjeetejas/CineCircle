package com.cinecircle.comment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("comments")
@CompoundIndexes({
        @CompoundIndex(name = "post_parent_created", def = "{'postId': 1, 'parentCommentId': 1, 'createdAt': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    @Id
    private String id;
    private String postId;
    private String authorId;
    private String authorUsername;
    private String body;
    /** Set for replies; replies are only one level deep. */
    private String parentCommentId;
    private boolean deleted;
    private Instant createdAt;
}

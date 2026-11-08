package com.cinecircle.vote;

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

/** Upvote only. Unique (postId, userId) makes double-clicks idempotent. */
@Document("votes")
@CompoundIndexes({
        @CompoundIndex(name = "post_user_unique", def = "{'postId': 1, 'userId': 1}", unique = true),
        @CompoundIndex(name = "user_post", def = "{'userId': 1, 'postId': 1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vote {

    @Id
    private String id;
    private String postId;
    private String userId;
    private Instant createdAt;
}

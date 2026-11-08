package com.cinecircle.follow;

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

@Document("follows")
@CompoundIndexes({
        @CompoundIndex(name = "follower_followee_unique", def = "{'followerId': 1, 'followeeId': 1}", unique = true),
        @CompoundIndex(name = "followee_status_created", def = "{'followeeId': 1, 'status': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "follower_status_created", def = "{'followerId': 1, 'status': 1, 'createdAt': -1}")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Follow {

    @Id
    private String id;
    private String followerId;
    private String followeeId;
    /** ACCEPTED or PENDING only. */
    private FollowStatus status;
    private Instant createdAt;
}

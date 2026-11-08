package com.cinecircle.follow;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository extends MongoRepository<Follow, String> {

    Optional<Follow> findByFollowerIdAndFolloweeId(String followerId, String followeeId);

    boolean existsByFollowerIdAndFolloweeIdAndStatus(String followerId, String followeeId, FollowStatus status);

    List<Follow> findByFollowerIdAndStatus(String followerId, FollowStatus status);

    List<Follow> findByFolloweeIdAndStatusOrderByCreatedAtDesc(String followeeId, FollowStatus status);
}

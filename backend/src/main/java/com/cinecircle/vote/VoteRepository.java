package com.cinecircle.vote;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;

public interface VoteRepository extends MongoRepository<Vote, String> {

    List<Vote> findByUserIdAndPostIdIn(String userId, Collection<String> postIds);

    boolean existsByPostIdAndUserId(String postId, String userId);

    long deleteByPostId(String postId);
}

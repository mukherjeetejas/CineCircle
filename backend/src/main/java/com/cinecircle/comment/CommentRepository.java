package com.cinecircle.comment;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommentRepository extends MongoRepository<Comment, String> {

    long deleteByPostId(String postId);
}

package com.cinecircle.summary;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface MovieReviewSummaryRepository extends MongoRepository<MovieReviewSummary, String> {

    Optional<MovieReviewSummary> findByTmdbId(Long tmdbId);
}

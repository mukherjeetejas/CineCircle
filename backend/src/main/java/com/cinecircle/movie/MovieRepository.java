package com.cinecircle.movie;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MovieRepository extends MongoRepository<Movie, String> {

    Optional<Movie> findByTmdbId(Long tmdbId);

    List<Movie> findByTmdbIdIn(Collection<Long> tmdbIds);
}

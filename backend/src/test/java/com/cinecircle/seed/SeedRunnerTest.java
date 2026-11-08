package com.cinecircle.seed;

import com.cinecircle.comment.CommentRepository;
import com.cinecircle.config.AppProperties;
import com.cinecircle.follow.FollowRepository;
import com.cinecircle.follow.FollowService;
import com.cinecircle.movie.Movie;
import com.cinecircle.movie.MovieService;
import com.cinecircle.movie.tmdb.TmdbModels;
import com.cinecircle.movie.tmdb.TmdbUnavailableException;
import com.cinecircle.post.PostRepository;
import com.cinecircle.user.UserCounters;
import com.cinecircle.user.UserRepository;
import com.cinecircle.vote.VoteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SeedRunnerTest {

    private final MovieService movieService = mock(MovieService.class);

    private SeedRunner runner(String token) {
        AppProperties props = new AppProperties("http://localhost:8080",
                new AppProperties.Tmdb(token, "http://tmdb.test/3", Duration.ofSeconds(5)),
                new AppProperties.Jwt("x".repeat(32), Duration.ofDays(7)), true,
                new AppProperties.Cors(List.of("http://localhost:5173")));
        return new SeedRunner(props, mock(UserRepository.class), mock(FollowRepository.class),
                mock(PostRepository.class), mock(VoteRepository.class), mock(CommentRepository.class),
                movieService, mock(FollowService.class), mock(PasswordEncoder.class), mock(UserCounters.class),
                mock(MongoTemplate.class));
    }

    @Test
    void loadMoviesRetriesOnceAfterFailure() {
        TmdbModels.MovieResult r = new TmdbModels.MovieResult(603L, "The Matrix", "The Matrix", "1999-03-30",
                "", "/m.jpg", null, 8.2);
        when(movieService.trendingResults())
                .thenThrow(new TmdbUnavailableException("reset"))
                .thenReturn(List.of(r));
        when(movieService.getOrFetch(603L)).thenReturn(Optional.of(Movie.builder().tmdbId(603L).build()));

        List<Movie> movies = runner("token").loadMovies();

        assertThat(movies).hasSize(1);
        verify(movieService, times(2)).trendingResults();
    }

    @Test
    void noRetryWithoutToken() {
        when(movieService.trendingResults()).thenThrow(new TmdbUnavailableException("not configured"));

        assertThat(runner("").loadMovies()).isEmpty();
        verify(movieService, times(1)).trendingResults();
    }
}

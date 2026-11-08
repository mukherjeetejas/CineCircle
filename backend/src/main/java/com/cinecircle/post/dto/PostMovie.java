package com.cinecircle.post.dto;

/** Movie chip on a post: {tmdbId,title,releaseYear,posterUrl} */
public record PostMovie(long tmdbId, String title, Integer releaseYear, String posterUrl) {
}

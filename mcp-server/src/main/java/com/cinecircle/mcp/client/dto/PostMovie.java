package com.cinecircle.mcp.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Movie chip on a post: {@code {tmdbId,title,releaseYear,posterUrl}} */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PostMovie(long tmdbId, String title, Integer releaseYear, String posterUrl) {
}

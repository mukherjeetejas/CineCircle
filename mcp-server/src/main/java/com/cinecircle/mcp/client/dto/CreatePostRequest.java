package com.cinecircle.mcp.client.dto;

/** JSON part {@code post} of {@code POST /posts}: {@code {tmdbId,title,body,rating,containsSpoilers,imageType}}. */
public record CreatePostRequest(long tmdbId, String title, String body, int rating, boolean containsSpoilers,
                                String imageType) {

    public static final String IMAGE_TYPE_TMDB_POSTER = "TMDB_POSTER";
}

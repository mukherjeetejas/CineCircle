package com.cinecircle.common;

import com.cinecircle.config.AppProperties;
import org.springframework.stereotype.Component;

/** Builds the absolute URLs mandated by CLAUDE.md. */
@Component
public class UrlBuilder {

    public static final String TMDB_POSTER_BASE = "https://image.tmdb.org/t/p/w500";
    public static final String TMDB_BACKDROP_BASE = "https://image.tmdb.org/t/p/w1280";

    private final String imageBase;

    public UrlBuilder(AppProperties props) {
        String base = props.publicUrl() == null ? "http://localhost:8080" : props.publicUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.imageBase = base + "/api/images/";
    }

    public String image(String imageId) {
        return imageId == null || imageId.isBlank() ? null : imageBase + imageId;
    }

    public String poster(String posterPath) {
        return posterPath == null || posterPath.isBlank() ? null : TMDB_POSTER_BASE + posterPath;
    }

    public String backdrop(String backdropPath) {
        return backdropPath == null || backdropPath.isBlank() ? null : TMDB_BACKDROP_BASE + backdropPath;
    }
}

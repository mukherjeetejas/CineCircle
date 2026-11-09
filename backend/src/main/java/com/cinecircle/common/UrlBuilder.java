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
        // app.public-base-url (APP_PUBLIC_BASE_URL, local default in application.yml / AppProperties)
        if (props.publicBaseUrl() == null || props.publicBaseUrl().isBlank()) {
            throw new IllegalStateException("app.public-base-url must not be blank");
        }
        String base = props.publicBaseUrl().strip();
        while (base.endsWith("/")) {
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

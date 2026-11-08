package com.cinecircle.mcp.format;

import com.cinecircle.mcp.config.CineCircleProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

/** Links into the CineCircle web app (default {@code http://localhost:5173}). */
@Component
public class Links {

    private final String webUrl;

    public Links(CineCircleProperties props) {
        this.webUrl = props.webUrl();
    }

    public String post(String id) {
        return webUrl + "/posts/" + UriUtils.encodePathSegment(id, StandardCharsets.UTF_8);
    }

    public String movie(long tmdbId) {
        return webUrl + "/movies/" + tmdbId;
    }

    public String user(String username) {
        return webUrl + "/u/" + UriUtils.encodePathSegment(username, StandardCharsets.UTF_8);
    }
}

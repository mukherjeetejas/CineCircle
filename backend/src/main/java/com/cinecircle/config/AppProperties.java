package com.cinecircle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

/**
 * {@code app.*}.
 *
 * @param publicBaseUrl public URL of this backend ({@code APP_PUBLIC_BASE_URL}), used for absolute image URLs
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String publicBaseUrl,
        Tmdb tmdb,
        Jwt jwt,
        @DefaultValue("true") boolean seed,
        Cors cors
) {
    public record Tmdb(String token,
                       @DefaultValue("https://api.themoviedb.org/3") String baseUrl,
                       @DefaultValue("5s") Duration timeout) {
    }

    public record Jwt(String secret, @DefaultValue("7d") Duration ttl) {
    }

    /** {@code app.cors.origins}: comma-separated in the env var ({@code APP_CORS_ORIGINS}), bound to a list. */
    public record Cors(@DefaultValue("http://localhost:5173") List<String> origins) {
    }
}

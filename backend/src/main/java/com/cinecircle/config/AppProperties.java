package com.cinecircle.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @DefaultValue("http://localhost:8080") String publicUrl,
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

    public record Cors(@DefaultValue("http://localhost:5173") List<String> origins) {
    }
}

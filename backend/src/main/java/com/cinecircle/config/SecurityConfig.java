package com.cinecircle.config;

import com.cinecircle.security.JsonSecurityHandlers;
import com.cinecircle.security.JwtAuthFilter;
import com.cinecircle.security.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import com.cinecircle.security.LongPasswordBCryptEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                   JsonSecurityHandlers handlers) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(handlers.entryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                        // "me" routes always require auth (must precede /users/{username} matchers)
                        .requestMatchers("/api/users/me", "/api/users/me/**").authenticated()
                        // public (token optional)
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/users/search",
                                "/api/users/suggestions",
                                "/api/users/*",
                                "/api/users/*/posts",
                                "/api/users/*/followers",
                                "/api/users/*/following",
                                "/api/movies/**",
                                "/api/posts/popular",
                                "/api/posts/*",
                                "/api/posts/*/comments",
                                "/api/images/*").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins(props));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Location"));
        config.setAllowCredentials(false);
        config.setMaxAge(Duration.ofHours(1));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }

    /**
     * {@code app.cors.origins} ({@code APP_CORS_ORIGINS}, comma-separated). Entries are trimmed, a trailing slash
     * is dropped (browsers send origins without one) and blanks are ignored.
     */
    public static List<String> allowedOrigins(AppProperties props) {
        List<String> raw = props.cors() == null || props.cors().origins() == null
                ? List.of() : props.cors().origins();
        List<String> out = raw.stream()
                .flatMap(o -> java.util.Arrays.stream(o.split(",")))
                .map(String::strip)
                .map(o -> o.endsWith("/") ? o.substring(0, o.length() - 1) : o)
                .filter(o -> !o.isEmpty())
                .distinct()
                .toList();
        if (out.isEmpty()) {
            throw new IllegalStateException("app.cors.origins must list at least one origin");
        }
        return out;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new LongPasswordBCryptEncoder();
    }
}

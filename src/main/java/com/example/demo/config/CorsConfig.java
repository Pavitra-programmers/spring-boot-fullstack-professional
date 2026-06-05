package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * TICKET LF-201 — CORS Configuration
 *
 * Root cause of the original bug: missing CorsConfigurationSource bean meant
 * Spring Security's filter chain blocked all cross-origin preflight (OPTIONS)
 * requests, causing the frontend on localhost:3000 to see CORS errors.
 *
 * Fix:
 *  1. Define a CorsConfigurationSource bean with explicit allowed origins.
 *  2. Wire it into SecurityConfig via http.cors(...) so the Security filter
 *     chain (not MVC) handles CORS — this covers ALL endpoints including
 *     those that return 401/403 before the controller is reached.
 *  3. Externalize allowed origins to application.yml / env var so each
 *     environment (dev/staging/prod) overrides without a code change.
 */
@Configuration
public class CorsConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Support comma-separated list: e.g. "http://localhost:3000,https://app.prod.com"
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L); // Cache preflight response for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

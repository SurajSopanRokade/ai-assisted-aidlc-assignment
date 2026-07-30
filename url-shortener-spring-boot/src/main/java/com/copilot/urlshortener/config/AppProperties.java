package com.copilot.urlshortener.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Typed configuration for everything environment-specific.
 *
 * <p>Bound and validated at startup, so a missing or malformed value fails the
 * boot rather than surfacing as a broken response later.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /**
     * Public origin used to build {@code short_url}, e.g. {@code https://sho.rt}.
     *
     * <p>Deliberately configured rather than read from the request's Host
     * header. Host is client-controlled: deriving the URL from it means a
     * request carrying {@code Host: evil.example.com} gets back a link
     * advertising {@code http://evil.example.com/abc123} — a ready-made
     * phishing primitive and a cache-poisoning vector.
     */
    @NotBlank
    private String baseUrl = "http://localhost:8080";

    private final Cors cors = new Cors();
    private final RateLimit rateLimit = new RateLimit();

    /** Normalised base URL with any trailing slash removed. */
    public String normalisedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Getter
    @Setter
    public static class Cors {
        /** Exact origins permitted to call the API from a browser. No wildcards. */
        private List<String> allowedOrigins = List.of("http://localhost:3000");
    }

    @Getter
    @Setter
    public static class RateLimit {
        private boolean enabled = true;

        /** Requests permitted per client per fixed one-minute window. */
        @Min(1)
        private int requestsPerMinute = 60;
    }
}

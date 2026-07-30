package com.copilot.urlshortener.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Clock;

/**
 * Web-layer wiring. Kept out of the {@code @SpringBootApplication} class so the
 * bootstrap class does nothing but bootstrap.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    /**
     * CORS is restricted to an explicit origin list from configuration.
     *
     * <p>Credentials are not enabled and no wildcard is used: the API is
     * unauthenticated, so a permissive policy would let any site drive a user's
     * browser into creating links on their behalf.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.getCors().getAllowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Content-Type", "X-Request-Id")
                .exposedHeaders("X-Request-Id", "Retry-After")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * Injecting a {@link Clock} rather than calling {@code now()} statically
     * lets time-dependent behaviour (link expiry, rate-limit windows) be tested
     * deterministically with {@link Clock#fixed}, instead of with sleeps.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}

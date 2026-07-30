package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.config.AppProperties;
import com.copilot.urlshortener.dto.ErrorResponse;
import com.copilot.urlshortener.dto.url.AnalyticsResponse;
import com.copilot.urlshortener.dto.url.ShortenRequest;
import com.copilot.urlshortener.dto.url.ShortenResponse;
import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.service.ShortenerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "URLs", description = "Create, resolve and measure short links")
public class UrlController {

    /**
     * Bounds the path variable before it reaches the service, so obviously
     * malformed codes are rejected as input errors instead of becoming
     * database lookups. Mirrors the alias rules in ShortenRequest.
     */
    private static final String SHORT_CODE_PATTERN = "^[a-zA-Z0-9]{3,16}$";

    private final ShortenerService shortenerService;
    private final AppProperties properties;

    @PostMapping("/shorten")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a short link",
            description = "Allocates a short code for the supplied URL, optionally using a custom alias "
                    + "and an expiry timestamp.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Short link created"),
            @ApiResponse(responseCode = "400", description = "Malformed request body",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Custom alias already in use",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Rate limit exceeded",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ShortenResponse shortenUrl(@Valid @RequestBody ShortenRequest request) {
        Url record = shortenerService.createShortUrl(
                request.getOriginalUrl(), request.getCustomAlias(), request.getExpiresAt());

        return ShortenResponse.builder()
                .id(record.getId())
                .originalUrl(record.getOriginalUrl())
                .shortCode(record.getShortCode())
                // Built from configuration, never from the request's Host
                // header — see AppProperties#baseUrl.
                .shortUrl(properties.normalisedBaseUrl() + "/" + record.getShortCode())
                .createdAt(record.getCreatedAt())
                .expiresAt(record.getExpiresAt())
                .build();
    }

    @GetMapping("/analytics/{shortCode}")
    @Operation(summary = "Read link analytics",
            description = "Click count, creation time, expiry state and last-clicked timestamp.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Analytics returned"),
            @ApiResponse(responseCode = "404", description = "Unknown short code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public AnalyticsResponse getAnalytics(
            @PathVariable @Pattern(regexp = SHORT_CODE_PATTERN, message = "short code must be 3-16 alphanumeric characters")
            String shortCode) {
        return shortenerService.getAnalytics(shortCode);
    }

    /**
     * Redirects to the stored destination and records the click.
     *
     * <p>307 rather than 301: a permanent redirect is cached by browsers and
     * intermediaries, which would both freeze the destination and make click
     * counts silently wrong once the cache warms.
     *
     * <p>{@code Cache-Control: no-store} is set for the same reason — a cached
     * 307 would bypass the service on subsequent clicks.
     *
     * <p>Returns {@code ResponseEntity} rather than {@code RedirectView}: the
     * view resolver applies its own URL processing to the target, which is
     * caller-supplied data and should be passed through untouched.
     */
    @GetMapping("/{shortCode}")
    @Operation(summary = "Resolve a short link",
            description = "Redirects to the original URL and records a click event.")
    @ApiResponses({
            @ApiResponse(responseCode = "307", description = "Redirect to the original URL"),
            @ApiResponse(responseCode = "404", description = "Unknown short code",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "410", description = "Link has expired",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public ResponseEntity<Void> redirectShortUrl(
            @PathVariable @Pattern(regexp = SHORT_CODE_PATTERN, message = "short code must be 3-16 alphanumeric characters")
            String shortCode) {
        String target = shortenerService.resolveAndRecordClick(shortCode);

        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(URI.create(target))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }
}

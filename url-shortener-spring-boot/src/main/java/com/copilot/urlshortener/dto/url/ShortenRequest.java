package com.copilot.urlshortener.dto.url;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortenRequest {

    /**
     * Only http and https are accepted. Rejecting other schemes at the edge
     * keeps {@code javascript:} and {@code data:} payloads out of the database,
     * so a stored destination can never become a script-execution sink for any
     * client that renders it.
     */
    @JsonProperty("original_url")
    @NotBlank(message = "original_url must not be empty")
    @Pattern(regexp = "^(http://|https://).*", message = "original_url must start with http:// or https://")
    @Size(max = 2048, message = "original_url exceeds maximum length of 2048 characters")
    private String originalUrl;

    /**
     * Alphanumeric only: a short code becomes a path segment, so permitting
     * {@code /}, {@code .} or {@code %} would let a caller craft a code that
     * changes how the redirect route is matched.
     */
    @JsonProperty("custom_alias")
    @Size(min = 3, max = 16, message = "custom_alias must be between 3 and 16 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]*$", message = "custom_alias must be alphanumeric")
    private String customAlias;

    /**
     * Optional expiry. Must be in the future: accepting a past timestamp
     * created a link that returned 410 on its very first use, which is never
     * what the caller intended and is confusing to debug after the fact.
     */
    @JsonProperty("expires_at")
    @Future(message = "expires_at must be in the future")
    private LocalDateTime expiresAt;
}

package com.copilot.urlshortener.dto.url;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShortenRequest {

    @JsonProperty("original_url")
    @NotBlank(message = "original_url must not be empty")
    @Pattern(regexp = "^(http://|https://).*", message = "original_url must start with http:// or https://")
    @Size(max = 2048, message = "original_url exceeds maximum length of 2048 characters")
    private String originalUrl;

    @JsonProperty("custom_alias")
    @Size(min = 3, max = 16, message = "custom_alias must be between 3 and 16 characters")
    @Pattern(regexp = "^[a-zA-Z0-9]*$", message = "custom_alias must be alphanumeric")
    private String customAlias;

    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
}

package com.copilot.urlshortener.dto.url;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortenResponse {
    private Long id;
    
    @JsonProperty("original_url")
    private String originalUrl;
    
    @JsonProperty("short_code")
    private String shortCode;
    
    @JsonProperty("short_url")
    private String shortUrl;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;
}

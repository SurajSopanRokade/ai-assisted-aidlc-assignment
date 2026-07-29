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
public class AnalyticsResponse {
    @JsonProperty("short_code")
    private String shortCode;
    
    @JsonProperty("original_url")
    private String originalUrl;
    
    @JsonProperty("click_count")
    private Integer clickCount;
    
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    
    @JsonProperty("last_clicked_at")
    private LocalDateTime lastClickedAt;
}

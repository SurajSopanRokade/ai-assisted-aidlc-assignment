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

    /** Null for links that never expire. */
    @JsonProperty("expires_at")
    private LocalDateTime expiresAt;

    /**
     * Whether the link is past its expiry right now. Returned so a client does
     * not have to re-implement the comparison — and get the timezone wrong —
     * to render link state. Analytics stay readable after expiry by design:
     * the owner still needs the historical numbers.
     */
    @JsonProperty("expired")
    private boolean expired;

    /** Null if the link has never been clicked. */
    @JsonProperty("last_clicked_at")
    private LocalDateTime lastClickedAt;
}

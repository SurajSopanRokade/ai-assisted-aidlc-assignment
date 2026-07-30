package com.copilot.urlshortener.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The rate limiter is disabled in the shared test profile so it cannot
 * interfere with other suites; this class re-enables it with a small quota.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.requests-per-minute=3"
})
class RateLimitFilterIntegrationTest {

    private static final String VALID_BODY = "{\"original_url\":\"https://example.com\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("requests beyond the quota are refused with 429 and a Retry-After hint")
    void refusesBeyondQuota() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/shorten")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(VALID_BODY))
                    .andExpect(status().isCreated());
        }

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                // The 429 must use the same error envelope as every other
                // failure; it is produced from a filter, which bypasses the
                // controller advice unless explicitly routed through it.
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("reads are never rate limited")
    void readsAreNotLimited() throws Exception {
        // The redirect path is the product. Throttling it would degrade
        // legitimate traffic without preventing the abuse the limit targets.
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(get("/notarealcode")).andExpect(status().isNotFound());
        }
    }
}

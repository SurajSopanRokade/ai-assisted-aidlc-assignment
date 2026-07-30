package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the HTTP contract through the full Spring stack.
 *
 * <p>Runs against the {@code test} profile (enforced by the surefire config in
 * pom.xml), so it uses target/test-url-shortener.db. That matters because the
 * setup below calls {@code deleteAll()}: without a pinned profile it would
 * truncate whatever database happened to be configured, including a
 * developer's local one.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UrlApiIntegrationTest {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UrlRepository urlRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        urlRepository.deleteAll();
    }

    @Test
    @DisplayName("shorten -> redirect -> analytics")
    void fullFlow() throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").exists())
                .andExpect(jsonPath("$.original_url").value("https://example.com"))
                .andReturn();

        String shortCode = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("short_code").asText();

        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("https://example.com"))
                // A cached redirect would silently stop counting clicks.
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(get("/analytics/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.click_count").value(1))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.last_clicked_at").exists());
    }

    @Test
    @DisplayName("short_url comes from configuration, not the client's Host header")
    void shortUrlIgnoresHostHeader() throws Exception {
        // Guards against Host-header injection: echoing the caller's Host would
        // produce links pointing at an attacker-chosen domain. app.base-url is
        // http://short.test in the test profile, so the configured value must
        // win no matter what the request claims.
        mockMvc.perform(post("/shorten")
                        .header("Host", "evil.example.com")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_url").value(org.hamcrest.Matchers.startsWith("http://short.test/")));
    }

    @Test
    @DisplayName("custom alias is honoured")
    void customAlias() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"custom_alias\":\"mylink\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").value("mylink"));
    }

    @Test
    @DisplayName("duplicate custom alias is a 409")
    void duplicateAlias() throws Exception {
        Url existing = new Url();
        existing.setOriginalUrl("https://example.com");
        existing.setShortCode("custom");
        urlRepository.save(existing);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://other.com\",\"custom_alias\":\"custom\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("non-http scheme is rejected")
    void invalidScheme() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"javascript:alert(1)\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.originalUrl").exists());
    }

    @Test
    @DisplayName("alias containing a path separator is rejected")
    void aliasWithPathSeparator() throws Exception {
        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"custom_alias\":\"a/../b\"}"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("expiry in the past is rejected at creation")
    void pastExpiryRejected() throws Exception {
        // Without @Future this is accepted and creates a link that is dead on
        // its first use — confusing to debug, and never what the caller meant.
        String past = LocalDateTime.now().minusDays(1).format(ISO);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"expires_at\":\"" + past + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.field_errors.expiresAt").value("expires_at must be in the future"));
    }

    @Test
    @DisplayName("future expiry is accepted and echoed back")
    void futureExpiryAccepted() throws Exception {
        String future = LocalDateTime.now().plusDays(1).format(ISO);

        mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"expires_at\":\"" + future + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expires_at").exists());
    }

    @Test
    @DisplayName("unknown short code is a 404")
    void unknownCode() throws Exception {
        mockMvc.perform(get("/missingcode"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    @DisplayName("expired link is a 410, and analytics remain readable")
    void expiredLink() throws Exception {
        Url expired = new Url();
        expired.setOriginalUrl("https://example.com");
        expired.setShortCode("expired");
        expired.setExpiresAt(LocalDateTime.now().minusDays(1));
        urlRepository.save(expired);

        mockMvc.perform(get("/expired")).andExpect(status().isGone());

        mockMvc.perform(get("/analytics/expired"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expired").value(true));
    }

    @Test
    @DisplayName("every response carries a correlation id")
    void correlationIdHeader() throws Exception {
        mockMvc.perform(get("/missingcode"))
                .andExpect(header().exists("X-Request-Id"));
    }
}

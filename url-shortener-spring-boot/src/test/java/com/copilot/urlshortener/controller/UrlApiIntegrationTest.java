package com.copilot.urlshortener.controller;

import com.copilot.urlshortener.dto.url.ShortenRequest;
import com.copilot.urlshortener.model.Url;
import com.copilot.urlshortener.repository.UrlRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class UrlApiIntegrationTest {

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
    void testFullFlow_Shorten_Redirect_Analytics() throws Exception {
        // 1. Shorten URL
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://example.com");

        MvcResult result = mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.short_code").exists())
                .andExpect(jsonPath("$.original_url").value("https://example.com"))
                .andReturn();

        String responseStr = result.getResponse().getContentAsString();
        String shortCode = objectMapper.readTree(responseStr).get("short_code").asText();

        // 2. Redirect (Click)
        mockMvc.perform(get("/" + shortCode))
                .andExpect(status().isTemporaryRedirect())
                .andExpect(redirectedUrl("https://example.com"));

        // 3. Analytics
        mockMvc.perform(get("/analytics/" + shortCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.click_count").value(1))
                .andExpect(jsonPath("$.last_clicked_at").exists());
    }

    @Test
    void testShorten_CustomAliasDuplicate() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("custom");
        urlRepository.save(url);

        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("https://anotherexample.com");
        req.setCustomAlias("custom");

        mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void testShorten_InvalidUrl() throws Exception {
        ShortenRequest req = new ShortenRequest();
        req.setOriginalUrl("not-a-url");

        mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void testRedirect_NotFound() throws Exception {
        mockMvc.perform(get("/missingcode"))
                .andExpect(status().isNotFound());
    }

    @Test
    void testRedirect_Expired() throws Exception {
        Url url = new Url();
        url.setOriginalUrl("https://example.com");
        url.setShortCode("expired");
        url.setExpiresAt(java.time.LocalDateTime.now().minusDays(1));
        urlRepository.save(url);

        mockMvc.perform(get("/expired"))
                .andExpect(status().isGone());
    }
}

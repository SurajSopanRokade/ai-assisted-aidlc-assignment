package com.copilot.urlshortener.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the error contract itself.
 *
 * <p>Two failure modes are easy to reintroduce and invisible in a code review:
 * returning HTTP 500 for an ordinary client mistake, and letting exception text
 * reach the response body. A malformed date surfaces Jackson's internal type
 * names; a failed write surfaces the raw {@code insert into click_events ...}
 * statement. Both are information disclosure (CWE-209), and reporting a caller
 * error as a server fault also corrupts error-rate alerting.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ErrorContractIntegrationTest {

    /**
     * Fragments that must never appear in a response body. Deliberately broad:
     * the point is to fail loudly if any future handler starts echoing
     * exception text back to callers.
     */
    private static final List<String> FORBIDDEN_FRAGMENTS = List.of(
            "exception", "org.springframework", "org.hibernate", "java.lang", "java.time",
            "select ", "insert ", "update ", "sqlite", "jdbc", "at com.copilot", "nested");

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("unparseable date is a 400, not a 500")
    void malformedDateIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"https://example.com\",\"expires_at\":\"not-a-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists())
                .andReturn();

        assertNoInternalDetail(result);
    }

    @Test
    @DisplayName("syntactically invalid JSON is a 400 with no parser internals")
    void malformedJsonIsClientError() throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\": "))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertNoInternalDetail(result);
    }

    @Test
    @DisplayName("wrong JSON type for a field is a 400")
    void wrongFieldType() throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":{\"nested\":true}}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertNoInternalDetail(result);
    }

    @Test
    @DisplayName("empty body is a 400")
    void emptyBody() throws Exception {
        mockMvc.perform(post("/shorten").contentType(MediaType.APPLICATION_JSON).content(""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("validation failure names the offending field without leaking types")
    void validationFailureShape() throws Exception {
        MvcResult result = mockMvc.perform(post("/shorten")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"original_url\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Validation failed"))
                .andExpect(jsonPath("$.field_errors").exists())
                .andReturn();

        assertNoInternalDetail(result);
    }

    @Test
    @DisplayName("a short code that cannot exist is rejected as input, not looked up")
    void malformedShortCode() throws Exception {
        mockMvc.perform(get("/a"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("404 body carries no internal detail")
    void notFoundShape() throws Exception {
        MvcResult result = mockMvc.perform(get("/doesnotexist"))
                .andExpect(status().isNotFound())
                .andReturn();

        assertNoInternalDetail(result);
    }

    /**
     * A response body may contain the caller's own input echoed back, but never
     * framework, driver or SQL internals.
     */
    private void assertNoInternalDetail(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString().toLowerCase(Locale.ROOT);
        for (String fragment : FORBIDDEN_FRAGMENTS) {
            assertFalse(body.contains(fragment),
                    () -> "Response leaked internal detail '" + fragment + "': " + body);
        }
    }
}

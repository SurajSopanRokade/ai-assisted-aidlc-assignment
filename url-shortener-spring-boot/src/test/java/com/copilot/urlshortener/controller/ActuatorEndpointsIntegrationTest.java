package com.copilot.urlshortener.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the operational surface.
 *
 * <p>Listing {@code prometheus} in
 * {@code management.endpoints.web.exposure.include} does not create the
 * endpoint: without a Micrometer registry on the classpath it returns 404, with
 * nothing in the configuration to suggest anything is wrong. A documentation
 * claim about observability needs a test the same way a claim about behaviour
 * does.
 *
 * <p>{@code @AutoConfigureObservability} is required: Spring Boot disables
 * metrics <em>export</em> auto-configuration in tests by default, so without it
 * the scrape endpoint is absent from the test context even though it is present
 * at runtime. That distinction is worth knowing — a test asserting the endpoint
 * exists would otherwise fail against a perfectly healthy application, which is
 * the kind of false signal that gets tests deleted rather than fixed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorEndpointsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("health reports UP")
    void health() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("prometheus endpoint is served and exposes the business counters")
    void prometheusExposesBusinessMetrics() throws Exception {
        // Drive one of each so the counters are registered and non-zero.
        mockMvc.perform(post("/shorten")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"original_url\":\"https://example.com/metrics-probe\"}"));
        mockMvc.perform(get("/nosuchcode"));

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                // Counters named for the business event, not the framework
                // primitive: "how many links were created" is answerable
                // without knowing how the service is built.
                .andExpect(content().string(org.hamcrest.Matchers.containsString("urlshortener_links_created")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("urlshortener_redirects")));
    }

    @Test
    @DisplayName("redirect outcomes are tagged so failures are distinguishable from successes")
    void redirectOutcomesAreTagged() throws Exception {
        mockMvc.perform(get("/anothermissing"));

        mockMvc.perform(get("/actuator/metrics/urlshortener.redirects"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("outcome")));
    }

    @Test
    @DisplayName("the OpenAPI contract is generated and covers every public route")
    void openApiContract() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/shorten']").exists())
                .andExpect(jsonPath("$.paths['/{shortCode}']").exists())
                .andExpect(jsonPath("$.paths['/analytics/{shortCode}']").exists());
    }
}

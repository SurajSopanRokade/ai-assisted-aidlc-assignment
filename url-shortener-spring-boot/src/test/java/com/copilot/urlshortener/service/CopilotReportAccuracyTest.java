package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.copilot.CopilotResponse;
import com.copilot.urlshortener.dto.copilot.ValidationReport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps the curated engineering report honest.
 *
 * <p>A self-assessment that asserts work which was not done is worse than no
 * self-assessment: it is confidently wrong, and a reviewer who trusts it stops
 * looking. Documentation drifts from code silently — nothing else in the build
 * has an opinion about whether a written claim is still true.
 *
 * <p>So each claim of a fix is tied here to the artefact that proves it. If
 * someone reverts a mitigation but leaves the report saying "fixed", this test
 * fails. It cannot verify prose, but it can verify that the mechanism a claim
 * depends on still exists.
 */
class CopilotReportAccuracyTest {

    private static final Path SOURCE_ROOT = Path.of("src/main/java/com/copilot/urlshortener");
    private static final Path TEST_ROOT = Path.of("src/test/java/com/copilot/urlshortener");
    private static final Path RESOURCES = Path.of("src/main/resources");

    private static CopilotResponse report;

    @BeforeAll
    static void loadReport() throws IOException {
        ObjectMapper mapper = new ObjectMapper()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = CopilotEngineService.class.getClassLoader()
                .getResourceAsStream(CopilotEngineService.REPORT_RESOURCE)) {
            assertNotNull(in, "curated report resource must be on the classpath");
            report = mapper.readValue(in, CopilotResponse.class);
        }
    }

    @Test
    @DisplayName("report parses into a complete response")
    void reportIsComplete() {
        assertNotNull(report.getRequirementAnalysis());
        assertNotNull(report.getTaskDecomposition());
        assertNotNull(report.getEngineeringArtifacts());
        assertNotNull(report.getValidation());
        assertNotNull(report.getRiskAnalysis());
        assertNotNull(report.getFinalSummary());
        assertFalse(report.getTaskDecomposition().isEmpty());
    }

    @Test
    @DisplayName("claim: the catch-all no longer returns exception text")
    void errorDisclosureClaimHolds() throws IOException {
        String handler = read(SOURCE_ROOT.resolve("controller/GlobalExceptionHandler.java"));

        // The generic handler must not put the exception's own message into the
        // response body.
        assertFalse(handler.contains("\"Internal server error: \" + ex.getMessage()"),
                "the catch-all is echoing exception text; the report claims it does not");
        assertTrue(handler.contains("HttpMessageNotReadableException"),
                "malformed bodies must be handled as 400 rather than falling through to the catch-all");
        assertTrue(testExists("controller/ErrorContractIntegrationTest.java"),
                "the report cites this test as the evidence for the error-contract claim");
    }

    @Test
    @DisplayName("claim: click counting is atomic in the database")
    void atomicIncrementClaimHolds() throws IOException {
        String repository = read(SOURCE_ROOT.resolve("repository/UrlRepository.java"));
        assertTrue(repository.contains("set u.clickCount = u.clickCount + 1"),
                "the atomic increment the report relies on is gone");

        String service = read(SOURCE_ROOT.resolve("service/ShortenerService.java"));
        assertFalse(service.contains("setClickCount(record.getClickCount()"),
                "in-memory read-modify-write has returned; concurrent clicks would be lost again");
        assertTrue(testExists("controller/RedirectConcurrencyIntegrationTest.java"),
                "the report cites this test as the evidence for the concurrency claim");
    }

    @Test
    @DisplayName("claim: short_url is built from configuration, not the Host header")
    void baseUrlClaimHolds() throws IOException {
        String controller = read(SOURCE_ROOT.resolve("controller/UrlController.java"));
        assertFalse(controller.contains("getServerName()"),
                "short_url is being derived from the request again");
        assertTrue(controller.contains("normalisedBaseUrl()"),
                "short_url must come from AppProperties");
    }

    @Test
    @DisplayName("claim: analytics uses an aggregate, not a collection walk")
    void analyticsAggregateClaimHolds() throws IOException {
        String repository = read(SOURCE_ROOT.resolve("repository/ClickEventRepository.java"));
        assertTrue(repository.contains("max(c.clickedAt)"), "the max() aggregate is missing");

        String service = read(SOURCE_ROOT.resolve("service/ShortenerService.java"));
        assertFalse(service.contains("getClickEvents().stream()"),
                "analytics is walking the lazy collection again");
    }

    @Test
    @DisplayName("claim: the write path is rate limited and keyed on the socket address")
    void rateLimitClaimHolds() throws IOException {
        String filter = read(SOURCE_ROOT.resolve("web/RateLimitFilter.java"));
        assertTrue(filter.contains("getRemoteAddr()"), "rate limiting must key on the socket address");
        assertFalse(filter.contains("getHeader(\"X-Forwarded-For\")"),
                "keying on a client-supplied header lets the limit be bypassed by rotating it");
    }

    @Test
    @DisplayName("claim: SQLite is configured for WAL with a busy timeout")
    void concurrencyConfigClaimHolds() throws IOException {
        String properties = read(RESOURCES.resolve("application.properties"));
        assertTrue(properties.contains("journal_mode=WAL"), "WAL journaling is claimed by the report");
        assertTrue(properties.contains("busy_timeout"), "a busy timeout is claimed by the report");
    }

    @Test
    @DisplayName("claim: the production profile exists and does not infer schema changes")
    void productionProfileClaimHolds() throws IOException {
        Path prod = RESOURCES.resolve("application-prod.properties");
        assertTrue(Files.exists(prod),
                "docker-compose activates the prod profile; without this file it silently runs dev defaults");
        assertTrue(read(prod).contains("ddl-auto=${APP_DDL_AUTO:validate}"),
                "production must not mutate schema from entity classes by default");
    }

    @Test
    @DisplayName("every open item is labelled open, never silently dropped")
    void openItemsAreLabelled() {
        ValidationReport validation = report.getValidation();

        List<ValidationReport.ValidationFinding> all = Stream.of(
                        validation.getCode_review(),
                        validation.getSecurity_review(),
                        validation.getPerformance_review())
                .flatMap(List::stream)
                .toList();

        // Closed vocabulary for disposition. "partially addressed" is a real
        // state and is allowed, but it must be said out loud — the failure mode
        // this guards against is a half-done mitigation described as done.
        List<String> allowedDispositions = List.of(
                "fixed", "open", "verified", "partially addressed", "n/a");

        for (ValidationReport.ValidationFinding finding : all) {
            String severity = finding.getSeverity().toLowerCase(Locale.ROOT);
            assertTrue(allowedDispositions.stream().anyMatch(severity::contains),
                    () -> "finding '" + finding.getArea() + "' must declare a disposition from "
                            + allowedDispositions + ", got: " + finding.getSeverity());
        }

        // Anything not fully fixed has to point at where it is tracked,
        // otherwise "open" is just a word.
        for (ValidationReport.ValidationFinding finding : all) {
            String severity = finding.getSeverity().toLowerCase(Locale.ROOT);
            if (severity.contains("open") || severity.contains("partially addressed")) {
                assertTrue(severity.contains("documented"),
                        () -> "unresolved finding '" + finding.getArea()
                                + "' must name where it is tracked, got: " + finding.getSeverity());
            }
        }

        // The two largest known gaps must remain visible in the report. If
        // someone implements them, this assertion is the reminder to update it.
        String securityText = validation.getSecurity_review().toString().toLowerCase(Locale.ROOT);
        assertTrue(securityText.contains("authentication"), "the unauthenticated-API gap must stay declared");
        assertTrue(securityText.contains("open redirect"), "the open-redirect trade-off must stay declared");
    }

    @Test
    @DisplayName("the report does not describe itself as generated analysis")
    void reportDoesNotOverclaim() {
        // The endpoint replays curated content; calling it analysis would
        // misrepresent what a reader is looking at.
        String approach = report.getFinalSummary().getImplementation_approach().toLowerCase(Locale.ROOT);
        assertFalse(approach.contains("ai generated the architecture"),
                "the report must not attribute design decisions to the tool");
    }

    private static boolean testExists(String relative) {
        return Files.exists(TEST_ROOT.resolve(relative));
    }

    private static String read(Path path) throws IOException {
        assertTrue(Files.exists(path), () -> "expected file is missing: " + path);
        return Files.readString(path, StandardCharsets.UTF_8);
    }
}

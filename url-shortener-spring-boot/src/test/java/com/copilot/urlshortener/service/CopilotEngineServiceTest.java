package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.copilot.CopilotResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the routing decision and the generic heuristic path.
 *
 * <p>The heuristic path deserves real coverage because it is the one most
 * callers actually reach: it runs for every requirement that is not the URL
 * shortener, while the curated path only answers a single known input.
 */
class CopilotEngineServiceTest {

    private CopilotEngineService service;

    @BeforeEach
    void setUp() {
        service = new CopilotEngineService(new ObjectMapper());
        service.loadCuratedReport();
    }

    @Test
    @DisplayName("a URL shortener requirement returns the curated report")
    void curatedPath() {
        CopilotResponse response = service.runCopilot("Build a URL shortener with analytics");

        assertNotNull(response.getValidation());
        assertTrue(response.getTaskDecomposition().size() >= 10,
                "the curated report enumerates the full task breakdown");
        // Asserts the summary cites measured coverage rather than an estimate.
        // The exact test count changes as tests are added; the commitment that
        // the figure comes from JaCoCo should not.
        assertTrue(response.getValidation().getTest_coverage_summary().contains("JaCoCo"),
                "the coverage summary must cite a measured source, not an estimate");
    }

    @Test
    @DisplayName("marker matching is case-insensitive")
    void curatedPathIsCaseInsensitive() {
        assertEquals(
                service.runCopilot("build a URL SHORTENER").getTaskDecomposition().size(),
                service.runCopilot("build a url shortener").getTaskDecomposition().size());
    }

    @Test
    @DisplayName("an unrelated requirement falls back to the heuristic path")
    void genericPath() {
        CopilotResponse response = service.runCopilot(
                "Build a payroll reconciliation job that runs nightly");

        assertNotNull(response.getRequirementAnalysis());
        assertEquals(5, response.getTaskDecomposition().size(),
                "the generic skeleton is five tasks");
        assertTrue(response.getFinalSummary().getImplementation_approach()
                        .contains("No implementation was produced"),
                "the generic path must not imply that code was written");
    }

    @Test
    @DisplayName("unquantified adjectives are flagged as ambiguities")
    void flagsUnquantifiedLanguage() {
        CopilotResponse response = service.runCopilot(
                "The system must be scalable, secure and user-friendly");

        List<String> ambiguities = response.getRequirementAnalysis().getAmbiguities();
        String joined = String.join(" ", ambiguities).toLowerCase(Locale.ROOT);

        assertTrue(joined.contains("scalable"));
        assertTrue(joined.contains("secure"));
        assertTrue(joined.contains("user-friendly"));
    }

    @Test
    @DisplayName("a requirement with no numbers is flagged as having no measurable target")
    void flagsMissingQuantities() {
        CopilotResponse response = service.runCopilot("Build a reporting dashboard for the finance team");

        assertTrue(response.getRequirementAnalysis().getAmbiguities().stream()
                        .anyMatch(a -> a.contains("No quantitative target")),
                "absence of any figure is itself a finding");
    }

    @Test
    @DisplayName("a requirement containing figures is not flagged for missing quantities")
    void doesNotFlagQuantifiedRequirements() {
        CopilotResponse response = service.runCopilot(
                "Process 5000 records per minute with p99 latency under 200 ms");

        assertFalse(response.getRequirementAnalysis().getAmbiguities().stream()
                        .anyMatch(a -> a.contains("No quantitative target")));
    }

    @Test
    @DisplayName("clean input yields an explicit 'weak signal' note, not silence")
    void noFalseAllClear() {
        CopilotResponse response = service.runCopilot(
                "Store 100 audit records per day and retain them for 7 years");

        String joined = String.join(" ", response.getRequirementAnalysis().getAmbiguities());
        assertTrue(joined.contains("weak signal"),
                "string matching finding nothing must never read as a clearance");
    }

    @Test
    @DisplayName("non-functional requirements are inferred from keywords")
    void infersNonFunctionalRequirements() {
        CopilotResponse response = service.runCopilot(
                "Must be secure, performant, highly available and scalable under load");

        List<String> categories = response.getRequirementAnalysis().getNon_functional_requirements().stream()
                .map(nfr -> nfr.getCategory())
                .toList();

        assertTrue(categories.containsAll(List.of("Security", "Performance", "Availability", "Scalability")),
                () -> "expected all four categories to be inferred, got: " + categories);
    }

    @Test
    @DisplayName("a requirement with no recognised keywords still gets a baseline NFR")
    void defaultsToMaintainability() {
        CopilotResponse response = service.runCopilot("Add a button that exports the table to CSV");

        assertEquals(1, response.getRequirementAnalysis().getNon_functional_requirements().size());
        assertEquals("Maintainability",
                response.getRequirementAnalysis().getNon_functional_requirements().get(0).getCategory());
    }

    @Test
    @DisplayName("a single-sentence requirement still produces one functional requirement")
    void singleSentenceRequirement() {
        CopilotResponse response = service.runCopilot("Export data to CSV");

        assertEquals(1, response.getRequirementAnalysis().getFunctional_requirements().size());
    }

    @Test
    @DisplayName("functional requirements are capped so a long requirement cannot flood the response")
    void capsFunctionalRequirements() {
        String longRequirement = "One. Two. Three. Four. Five. Six. Seven. Eight. Nine. Ten. Eleven. Twelve.";

        CopilotResponse response = service.runCopilot(longRequirement);

        assertEquals(8, response.getRequirementAnalysis().getFunctional_requirements().size());
    }

    @Test
    @DisplayName("the generic validation report claims no review it did not perform")
    void genericValidationClaimsNothing() {
        CopilotResponse response = service.runCopilot("Build a nightly batch job for invoices");

        assertEquals("No implementation, therefore no coverage.",
                response.getValidation().getTest_coverage_summary());
        assertTrue(response.getValidation().getSecurity_review().get(0).getSeverity().contains("N/A"));
    }
}

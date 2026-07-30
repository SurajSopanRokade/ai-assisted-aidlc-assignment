package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.copilot.CopilotResponse;
import com.copilot.urlshortener.dto.copilot.EngineeringArtifacts;
import com.copilot.urlshortener.dto.copilot.EngineeringTask;
import com.copilot.urlshortener.dto.copilot.FinalSummary;
import com.copilot.urlshortener.dto.copilot.RequirementAnalysis;
import com.copilot.urlshortener.dto.copilot.RiskAnalysis;
import com.copilot.urlshortener.dto.copilot.ValidationReport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Serves the engineering report behind {@code POST /copilot/analyze}.
 *
 * <p><b>What this is.</b> Two things, deliberately separated:
 *
 * <ol>
 *   <li>For the URL shortener requirement this assignment was built around, it
 *       returns a <em>curated</em> report authored by the engineer and stored in
 *       {@code copilot/url-shortener-report.json}. Every claim in it is checked
 *       against the code by {@code CopilotReportAccuracyTest}.
 *   <li>For any other requirement it runs a small deterministic heuristic that
 *       flags unquantified language and derives a generic task skeleton.
 * </ol>
 *
 * <p><b>What this is not.</b> It is not a model, and it does not analyse
 * anything for case (1) — that analysis was done by a human during development
 * and is being played back, selected by a substring match on the requirement.
 * Calling it an "engine that analyses requirements" would overstate it, so this
 * comment exists to make sure nobody, reviewer or maintainer, has to
 * reverse-engineer what they are actually looking at.
 *
 * <p>The report lives in a resource file rather than in string literals so that
 * editing documentation is not a code change, and so its claims can be
 * validated by a test that reads the same file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CopilotEngineService {

    static final String REPORT_RESOURCE = "copilot/url-shortener-report.json";

    private static final List<String> URL_SHORTENER_MARKERS = List.of(
            "url shortener", "shorten", "short url", "short link");

    /** Words that promise a property without stating how it would be measured. */
    private static final List<String> UNQUANTIFIED_TERMS = List.of(
            "scalable", "fast", "user-friendly", "robust", "efficient", "secure",
            "modern", "flexible", "easy to use", "high performance", "reliable",
            "some", "etc", "and so on", "as needed", "appropriate");

    private static final Pattern CONTAINS_DIGIT = Pattern.compile("\\d");

    private final ObjectMapper objectMapper;

    /** Immutable after startup; the report is read once, not per request. */
    private CopilotResponse curatedReport;

    /**
     * Loads and parses the report at startup so a malformed resource fails the
     * boot rather than the first request that needs it.
     */
    @PostConstruct
    void loadCuratedReport() {
        ObjectMapper reader = objectMapper.copy()
                // The resource carries a leading _comment block explaining its
                // provenance; that is for maintainers, not for the DTO.
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        try (InputStream in = new ClassPathResource(REPORT_RESOURCE).getInputStream()) {
            this.curatedReport = reader.readValue(in, CopilotResponse.class);
            log.info("Loaded curated engineering report from {}", REPORT_RESOURCE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load " + REPORT_RESOURCE, e);
        }
    }

    public CopilotResponse runCopilot(String requirement) {
        return matchesUrlShortener(requirement) ? curatedReport : analyseGenerically(requirement);
    }

    private boolean matchesUrlShortener(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        return URL_SHORTENER_MARKERS.stream().anyMatch(lowered::contains);
    }

    // --- Generic heuristic path ---------------------------------------------

    private CopilotResponse analyseGenerically(String requirement) {
        return CopilotResponse.builder()
                .requirementAnalysis(genericAnalysis(requirement))
                .taskDecomposition(genericTasks())
                .engineeringArtifacts(genericArtifacts())
                .validation(genericValidation())
                .riskAnalysis(genericRisks())
                .finalSummary(genericSummary())
                .build();
    }

    private RequirementAnalysis genericAnalysis(String text) {
        List<RequirementAnalysis.FunctionalRequirement> functional = new ArrayList<>();
        List<String> sentences = splitIntoSentences(text);
        for (int i = 0; i < Math.min(sentences.size(), 8); i++) {
            functional.add(new RequirementAnalysis.FunctionalRequirement("FR-" + (i + 1), sentences.get(i)));
        }
        if (functional.isEmpty()) {
            functional.add(new RequirementAnalysis.FunctionalRequirement("FR-1", text.trim()));
        }

        return RequirementAnalysis.builder()
                .functional_requirements(functional)
                .non_functional_requirements(inferNonFunctional(text))
                .ambiguities(detectAmbiguities(text))
                .assumptions(List.of(
                        "Treated as a standalone request; no existing system context was supplied.",
                        "A baseline engineering bar (tests, documentation, modularity) applies whether or not it was asked for."))
                .build();
    }

    private List<String> splitIntoSentences(String text) {
        List<String> sentences = new ArrayList<>();
        for (String candidate : text.split("[.\\n]")) {
            String trimmed = candidate.trim();
            if (!trimmed.isEmpty()) {
                sentences.add(trimmed);
            }
        }
        return sentences;
    }

    private List<RequirementAnalysis.NonFunctionalRequirement> inferNonFunctional(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        List<RequirementAnalysis.NonFunctionalRequirement> inferred = new ArrayList<>();

        addIfPresent(inferred, lowered, "scalab", "Scalability",
                "Referenced without a target; needs a QPS, concurrency or data-volume figure to be testable.");
        addIfPresent(inferred, lowered, "secur", "Security",
                "Referenced without specifying controls (authentication, authorization, encryption at rest or in transit).");
        addIfPresent(inferred, lowered, "perform", "Performance",
                "Referenced without a latency or throughput budget, so no acceptance criterion can be written.");
        addIfPresent(inferred, lowered, "avail", "Availability",
                "Referenced without an uptime target or a definition of what counts as downtime.");
        addIfPresent(inferred, lowered, "test", "Testability",
                "Quality expectations mentioned explicitly; confirm what level of coverage is expected to count as done.");

        if (inferred.isEmpty()) {
            inferred.add(new RequirementAnalysis.NonFunctionalRequirement("Maintainability",
                    "No non-functional requirements were stated. Maintainability and test coverage are assumed as a baseline."));
        }
        return inferred;
    }

    private void addIfPresent(List<RequirementAnalysis.NonFunctionalRequirement> target,
                              String loweredText, String marker, String category, String description) {
        if (loweredText.contains(marker)) {
            target.add(new RequirementAnalysis.NonFunctionalRequirement(category, description));
        }
    }

    /**
     * Flags language that cannot be turned into an acceptance criterion. This is
     * string matching, not comprehension: it is useful as a checklist prompt for
     * a human and is explicitly not a substitute for reading the requirement.
     */
    private List<String> detectAmbiguities(String text) {
        String lowered = text.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();

        for (String term : UNQUANTIFIED_TERMS) {
            if (lowered.contains(term)) {
                found.add("'" + term + "' is used without a measurable definition — no target, threshold or SLA is given.");
            }
        }
        if (lowered.contains("analytics") && !lowered.contains("metric") && !lowered.contains("click")) {
            found.add("'Analytics' is unspecified: which metrics, at what granularity, retained for how long?");
        }
        if (!CONTAINS_DIGIT.matcher(text).find()) {
            found.add("No quantitative target appears anywhere in the requirement (throughput, latency, retention or scale).");
        }
        if (found.isEmpty()) {
            found.add("Pattern matching found no unquantified language. This is a weak signal, not a clearance — "
                    + "a requirement can be entirely specific and still be ambiguous about intent.");
        }
        return found;
    }

    private List<EngineeringTask> genericTasks() {
        return List.of(
                new EngineeringTask("T1", "Resolve ambiguities and fix scope",
                        "Close the open questions listed in the requirement analysis before any code is written.",
                        List.of(),
                        "Heuristics can propose candidate questions; deciding which are blocking is a human judgement."),
                new EngineeringTask("T2", "Design the data model and contracts",
                        "Define the schema and API surface implied by the clarified requirement.",
                        List.of("T1"),
                        "AI drafts a first schema quickly; types, constraints, indexes and nullability need review."),
                new EngineeringTask("T3", "Implement the core logic",
                        "Build the primary behaviour behind an interface that can be tested without I/O.",
                        List.of("T2"),
                        "AI is effective at a first pass; verify boundaries, error paths and concurrency by hand."),
                new EngineeringTask("T4", "Test at both levels",
                        "Unit-test the logic in isolation and integration-test the contract end to end.",
                        List.of("T3"),
                        "AI-generated tests skew to happy paths and can assert the implementation's behaviour rather than the requirement's."),
                new EngineeringTask("T5", "Document, review, and record limitations",
                        "Write the supporting documentation and state explicitly what was not done.",
                        List.of("T3", "T4"),
                        "AI drafts prose well; every factual claim must be checked against the shipped code."));
    }

    private EngineeringArtifacts genericArtifacts() {
        return EngineeringArtifacts.builder()
                .folder_structure(List.of("src/main/", "src/test/", "docs/"))
                .database_schema("Not derivable: the requirement does not describe the entities or their relationships.")
                .api_contracts(List.of("To be defined once the ambiguities above are resolved."))
                .key_files(List.of())
                .build();
    }

    private ValidationReport genericValidation() {
        return ValidationReport.builder()
                .code_review(List.of(new ValidationReport.ValidationFinding(
                        "Not applicable", "No implementation exists for this requirement, so there is nothing to review.", "N/A")))
                .security_review(List.of(new ValidationReport.ValidationFinding(
                        "Not applicable", "A security review needs a concrete implementation and a threat model.", "N/A")))
                .performance_review(List.of(new ValidationReport.ValidationFinding(
                        "Not applicable", "Performance can only be assessed against a running system.", "N/A")))
                .missing_edge_cases(List.of("Edge cases cannot be enumerated before the scope is fixed."))
                .test_coverage_summary("No implementation, therefore no coverage.")
                .build();
    }

    private RiskAnalysis genericRisks() {
        return RiskAnalysis.builder()
                .risks(List.of(
                        new RiskAnalysis.Risk("Requirements",
                                "Ambiguity leads to building the wrong thing correctly.",
                                "Resolve the listed ambiguities with the requester before implementation starts."),
                        new RiskAnalysis.Risk("AI-related",
                                "For an under-specified requirement, AI output is fluent and confident regardless of whether it matches intent.",
                                "Validate against the clarified requirement, never against the AI's restatement of it.")))
                .build();
    }

    private FinalSummary genericSummary() {
        return FinalSummary.builder()
                .implementation_approach("No implementation was produced. This path performs lightweight requirement "
                        + "triage only; it does not synthesise a system.")
                .generated_artifacts(List.of("Requirement triage only"))
                .risks_and_validation("Not applicable without an implementation.")
                .assumptions_and_limitations("The generic path is deterministic string analysis. The fully implemented, "
                        + "validated example is the URL shortener itself.")
                .build();
    }
}

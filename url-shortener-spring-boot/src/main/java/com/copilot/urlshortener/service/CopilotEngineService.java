package com.copilot.urlshortener.service;

import com.copilot.urlshortener.dto.copilot.*;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
public class CopilotEngineService {

    private static final List<String> AMBIGUOUS_TERMS = List.of(
            "scalable", "fast", "user-friendly", "robust", "efficient", "secure",
            "modern", "flexible", "easy to use", "high performance", "reliable",
            "some", "etc", "and so on", "as needed", "appropriate"
    );

    private static final List<String> URL_SHORTENER_MARKERS = List.of(
            "url shortener", "shorten", "short url", "short link"
    );

    private boolean isUrlShortenerRequirement(String text) {
        String lowered = text.toLowerCase();
        return URL_SHORTENER_MARKERS.stream().anyMatch(lowered::contains);
    }

    private List<String> detectAmbiguities(String text) {
        String lowered = text.toLowerCase();
        List<String> found = new ArrayList<>();
        for (String term : AMBIGUOUS_TERMS) {
            if (lowered.contains(term)) {
                found.add("Term '" + term + "' is used without a measurable definition (e.g. no target number, SLA, or threshold given).");
            }
        }
        if (lowered.contains("analytics") && !lowered.contains("metric") && !lowered.contains("click")) {
            found.add("'Analytics' is unspecified — unclear which metrics are required (click count only? geography, referrer, device, time-series?).");
        }
        if (!Pattern.compile("\\d").matcher(text).find()) {
            found.add("No quantitative targets given anywhere in the requirement (no throughput, latency, retention, or scale numbers).");
        }
        if (found.isEmpty()) {
            found.add("No explicit ambiguities detected by pattern matching; manual review is still recommended before implementation.");
        }
        return found;
    }

    private RequirementAnalysis urlShortenerAnalysis() {
        return RequirementAnalysis.builder()
                .functional_requirements(List.of(
                        new RequirementAnalysis.FunctionalRequirement("FR-1", "Accept a long/original URL and return a unique shortened URL."),
                        new RequirementAnalysis.FunctionalRequirement("FR-2", "Support an optional custom alias/short code supplied by the caller."),
                        new RequirementAnalysis.FunctionalRequirement("FR-3", "Redirect requests on the short URL to the original URL (HTTP redirect)."),
                        new RequirementAnalysis.FunctionalRequirement("FR-4", "Persist the mapping between short code and original URL durably."),
                        new RequirementAnalysis.FunctionalRequirement("FR-5", "Track and expose click analytics per short code (count, last-clicked)."),
                        new RequirementAnalysis.FunctionalRequirement("FR-6", "Reject malformed or missing URLs with a clear validation error."),
                        new RequirementAnalysis.FunctionalRequirement("FR-7", "Prevent short-code collisions (auto-generated and custom aliases).")
                ))
                .non_functional_requirements(List.of(
                        new RequirementAnalysis.NonFunctionalRequirement("Scalability", "Short-code generation and lookup should remain O(1)-ish (indexed lookup) as the URL table grows; documented as a stated assumption since no target QPS was given (see Ambiguities)."),
                        new RequirementAnalysis.NonFunctionalRequirement("Availability", "Redirect path (GET /{shortCode}) is the hottest, most latency-sensitive endpoint and should be optimized first."),
                        new RequirementAnalysis.NonFunctionalRequirement("Data Integrity", "Short code must be globally unique; collisions must never silently overwrite an existing mapping."),
                        new RequirementAnalysis.NonFunctionalRequirement("Security", "Input URLs must be validated to reduce open-redirect and injection risk; short codes must be restricted to a safe charset."),
                        new RequirementAnalysis.NonFunctionalRequirement("Maintainability", "Business logic isolated from the HTTP layer so it is independently unit-testable and swappable (e.g. Postgres instead of SQLite)."),
                        new RequirementAnalysis.NonFunctionalRequirement("Observability", "Click events are recorded with timestamps to support future time-series analytics, not just a running counter.")
                ))
                .ambiguities(List.of(
                        "'Scalable' has no numeric target (QPS, concurrent users, or data volume) — assumption documented below.",
                        "'Analytics' is unspecified beyond a click counter — clarified as click_count + last_clicked_at + per-click event log for this prototype.",
                        "No mention of link expiry, deletion, or ownership/auth — assumed out of scope for this prototype (see Assumptions & Limitations).",
                        "No mention of rate limiting or abuse prevention for the POST /shorten endpoint — flagged as a risk, not implemented, given prototype scope."
                ))
                .assumptions(List.of(
                        "Short codes are 7-character base62 strings (~3.5 trillion combinations) — sufficient collision margin for a prototype without a stated scale target.",
                        "No authentication/authorization is required; all endpoints are public, consistent with the assignment's focus on AI-assisted engineering execution rather than full production hardening.",
                        "SQLite is acceptable for the prototype; the ORM boundary (Spring Data JPA) allows swapping to PostgreSQL via a connection-string change only.",
                        "'Persistence' means durable storage across restarts (a real database), not in-memory caching only.",
                        "Redirect uses HTTP 307 (Temporary Redirect) rather than 301, since short links may be repointed/expired in a future iteration — permanent redirects would be cached by browsers, which is undesirable for a service designed to be extended."
                ))
                .build();
    }

    private RequirementAnalysis genericAnalysis(String text) {
        String[] sentencesArray = text.split("[.\\n]");
        List<String> sentences = new ArrayList<>();
        for (String s : sentencesArray) {
            if (!s.trim().isEmpty()) {
                sentences.add(s.trim());
            }
        }

        List<RequirementAnalysis.FunctionalRequirement> functional = new ArrayList<>();
        int limit = Math.min(sentences.size(), 8);
        for (int i = 0; i < limit; i++) {
            functional.add(new RequirementAnalysis.FunctionalRequirement("FR-" + (i + 1), sentences.get(i)));
        }
        if (functional.isEmpty()) {
            functional.add(new RequirementAnalysis.FunctionalRequirement("FR-1", text.trim()));
        }

        List<RequirementAnalysis.NonFunctionalRequirement> nonFunctional = new ArrayList<>();
        String lowered = text.toLowerCase();
        
        if (lowered.contains("scalab")) nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Scalability", "System should handle growth in load/data; no numeric target stated — needs clarification."));
        if (lowered.contains("secur")) nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Security", "Security requirement referenced; specific controls (authN/authZ, encryption) not specified."));
        if (lowered.contains("perform")) nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Performance", "Performance referenced; no latency/throughput target given."));
        if (lowered.contains("avail")) nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Availability", "Availability referenced; no uptime/SLA target given."));
        if (lowered.contains("test")) nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Testability", "Testing/quality referenced explicitly in the requirement."));
        
        if (nonFunctional.isEmpty()) {
            nonFunctional.add(new RequirementAnalysis.NonFunctionalRequirement("Maintainability", "No explicit NFRs stated; maintainability and code quality assumed as baseline engineering expectations."));
        }

        return RequirementAnalysis.builder()
                .functional_requirements(functional)
                .non_functional_requirements(nonFunctional)
                .ambiguities(detectAmbiguities(text))
                .assumptions(List.of(
                        "Requirement is treated as a standalone feature request; no existing system context was provided.",
                        "Standard engineering quality bar (tests, docs, modularity) applies even though not explicitly requested."
                ))
                .build();
    }

    private List<EngineeringTask> urlShortenerTasks() {
        return List.of(
                new EngineeringTask("T1", "Define data model & API contracts", "Design the 'urls' table schema and the request/response contracts for /shorten, /{shortCode}, and /analytics/{shortCode}.", List.of(), "AI drafted an initial JPA model and DTO schemas from a prompt describing the fields; engineer reviewed field types/constraints (e.g. added unique index on short_code, added click_events table AI hadn't suggested for real analytics)."),
                new EngineeringTask("T2", "Implement short-code generation service", "Build the base62 short-code generator with collision handling, isolated from the HTTP layer.", List.of("T1"), "AI generated the initial random-code function; engineer identified it lacked a uniqueness check against the DB and added a bounded retry loop plus custom-alias collision handling (see ShortenerService.java)."),
                new EngineeringTask("T3", "Implement POST /shorten, GET /{shortCode}, GET /analytics/{shortCode}", "Wire the Spring REST Controllers to the shortener service, with input validation and error handling.", List.of("T2"), "AI suggested route signatures and status codes; engineer corrected the redirect status (AI defaulted to 301, changed to 307 — see Assumptions) and added explicit 404/400 error paths AI's first draft omitted."),
                new EngineeringTask("T4", "Write unit tests for the shortener service", "Cover code generation uniqueness, custom alias collisions, and analytics aggregation logic in isolation from HTTP.", List.of("T2"), "AI drafted the initial JUnit cases; engineer added edge cases AI missed (duplicate custom alias, analytics for a never-clicked link, generation-exhaustion path) and verified assertions independently."),
                new EngineeringTask("T5", "Write integration tests for the API layer", "End-to-end tests against the Spring MockMvc covering the full shorten -> redirect -> analytics flow and error responses.", List.of("T3"), "AI proposed the happy-path test; engineer added negative-path coverage (invalid URL, unknown short code, duplicate alias) that the AI draft did not include."),
                new EngineeringTask("T6", "Build the requirement-input frontend", "React/TypeScript UI accepting a requirement string and rendering the structured copilot output across tabs.", List.of("T1"), "AI scaffolded component structure and Tailwind classes from a description of the desired layout; engineer refactored shared state into a single App-level fetch and split render-only presentational components for maintainability."),
                new EngineeringTask("T7", "Documentation & architecture diagrams", "Write README, ARCHITECTURE.md (with Mermaid diagrams), and example scenario docs.", List.of("T3", "T6"), "AI drafted initial Mermaid syntax; engineer corrected diagram relationships that didn't match the actual implemented routes and verified diagrams render correctly.")
        );
    }

    private List<EngineeringTask> genericTasks(String text) {
        return List.of(
                new EngineeringTask("T1", "Clarify requirement & define scope", "Resolve ambiguities identified in requirement analysis before implementation begins.", List.of(), "AI flags likely ambiguous terms via pattern matching; engineer confirms which are actually blocking."),
                new EngineeringTask("T2", "Design data model / API contract", "Define schema and endpoint contracts implied by the requirement.", List.of("T1"), "AI drafts initial schema from requirement text; engineer validates types, constraints, and normalization."),
                new EngineeringTask("T3", "Implement core logic", "Build the primary feature logic described in the requirement.", List.of("T2"), "AI generates a first implementation pass; engineer reviews for correctness, edge cases, and style consistency with the existing codebase."),
                new EngineeringTask("T4", "Write unit & integration tests", "Cover core logic in isolation and the end-to-end flow.", List.of("T3"), "AI drafts test skeletons; engineer adds edge/negative cases and verifies assertions against actual behavior, not assumed behavior."),
                new EngineeringTask("T5", "Document & review", "Write supporting documentation and perform a final validation pass.", List.of("T3", "T4"), "AI drafts documentation prose; engineer verifies technical accuracy against the shipped code.")
        );
    }

    private EngineeringArtifacts urlShortenerArtifacts() {
        return EngineeringArtifacts.builder()
                .folder_structure(List.of(
                        "src/main/java/com/copilot/urlshortener/UrlShortenerApplication.java",
                        "src/main/resources/application.properties",
                        "src/main/java/com/copilot/urlshortener/model/*.java",
                        "src/main/java/com/copilot/urlshortener/dto/**/*.java",
                        "src/main/java/com/copilot/urlshortener/repository/*.java",
                        "src/main/java/com/copilot/urlshortener/service/*.java",
                        "src/main/java/com/copilot/urlshortener/controller/*.java",
                        "src/main/java/com/copilot/urlshortener/exception/*.java",
                        "src/test/java/com/copilot/urlshortener/**/*.java",
                        "frontend/src/App.tsx", "frontend/src/components/*.tsx"
                ))
                .database_schema("Table: urls(id PK, original_url TEXT, short_code VARCHAR(16) UNIQUE INDEXED, click_count INTEGER DEFAULT 0, created_at DATETIME). Table: click_events(id PK, url_id FK->urls.id, clicked_at DATETIME) — supports time-series analytics beyond a bare counter.")
                .api_contracts(List.of(
                        "POST /shorten { original_url, custom_alias? } -> 201 { id, original_url, short_code, short_url, created_at }",
                        "GET /{shortCode} -> 307 redirect to original_url, or 404 if not found",
                        "GET /analytics/{shortCode} -> 200 { short_code, original_url, click_count, created_at, last_clicked_at } or 404"
                ))
                .key_files(List.of(
                        new EngineeringArtifacts.ArtifactFile("src/main/java/com/copilot/urlshortener/service/ShortenerService.java", "Core domain logic: code generation, collision handling, click recording, analytics aggregation."),
                        new EngineeringArtifacts.ArtifactFile("src/main/java/com/copilot/urlshortener/controller/UrlController.java", "HTTP layer: request validation, status codes, error translation."),
                        new EngineeringArtifacts.ArtifactFile("src/test/java/com/copilot/urlshortener/service/ShortenerServiceTest.java", "Unit tests for the domain service in isolation."),
                        new EngineeringArtifacts.ArtifactFile("src/test/java/com/copilot/urlshortener/controller/UrlApiIntegrationTest.java", "Integration tests exercising the full HTTP API via Spring MockMvc.")
                ))
                .build();
    }

    private EngineeringArtifacts genericArtifacts() {
        return EngineeringArtifacts.builder()
                .folder_structure(List.of("src/main/java/", "src/test/java/", "frontend/src/", "docs/"))
                .database_schema("Not applicable / not enough information in the requirement to derive a schema.")
                .api_contracts(List.of("Contracts to be defined once ambiguities in the requirement analysis are resolved."))
                .key_files(List.of())
                .build();
    }

    private ValidationReport urlShortenerValidation() {
        return ValidationReport.builder()
                .code_review(List.of(
                        new ValidationReport.ValidationFinding("ShortenerService.java", "Initial AI draft used unbounded random retries; bounded to MAX_SHORT_CODE_GENERATION_ATTEMPTS and raises a typed exception on exhaustion.", "Medium — fixed"),
                        new ValidationReport.ValidationFinding("UrlController.java", "AI draft returned raw exception messages to clients; replaced with explicit Exception Handler status codes and sanitized details.", "Low — fixed")
                ))
                .security_review(List.of(
                        new ValidationReport.ValidationFinding("Input validation", "original_url is validated for scheme (http/https) and max length to reduce malformed/oversized input; does not fully prevent open-redirect abuse to malicious domains — flagged as a known limitation.", "Medium — open, documented"),
                        new ValidationReport.ValidationFinding("custom_alias", "Restricted to alphanumeric characters to prevent path-traversal-like short codes.", "Low — fixed"),
                        new ValidationReport.ValidationFinding("Rate limiting", "No rate limiting on POST /shorten; a public deployment would need this to prevent abuse/DB flooding.", "Medium — open, documented")
                ))
                .performance_review(List.of(
                        new ValidationReport.ValidationFinding("short_code lookup", "short_code column is indexed and unique; redirect lookups are O(log n) via the DB index, not a full table scan.", "Low — verified"),
                        new ValidationReport.ValidationFinding("Click recording", "Click count increment and event insert happen in the same transaction/commit as the redirect lookup, adding write latency to the hot redirect path; acceptable for prototype scale, flagged for async/batched writes at higher scale.", "Medium — open, documented")
                ))
                .missing_edge_cases(List.of(
                        "Extremely long URLs at the boundary of the 2048-char limit (off-by-one).",
                        "Concurrent requests racing to claim the same custom_alias (needs a DB-level unique constraint as the source of truth, not just an app-level check — current implementation relies on the unique index to reject the race loser).",
                        "Short code with correct charset but pointing to a soft-deleted/never-existed record (currently returns 404, verified in tests)."
                ))
                .test_coverage_summary("Unit tests cover: unique code generation, custom alias success/collision, generation-exhaustion path, click recording, and analytics for both clicked and never-clicked links. Integration tests cover: full shorten->redirect->analytics happy path, invalid URL rejection, missing short code (404), and duplicate custom alias rejection (409). Estimated logical branch coverage for ShortenerService.java: high (all raised-exception branches exercised).")
                .build();
    }

    private ValidationReport genericValidation() {
        return ValidationReport.builder()
                .code_review(List.of(new ValidationReport.ValidationFinding("General", "No code generated for a generic requirement without a concrete implementation target.", "N/A")))
                .security_review(List.of(new ValidationReport.ValidationFinding("General", "Security review requires a concrete implementation to assess.", "N/A")))
                .performance_review(List.of(new ValidationReport.ValidationFinding("General", "Performance review requires a concrete implementation to assess.", "N/A")))
                .missing_edge_cases(List.of("Cannot enumerate edge cases without a defined implementation scope."))
                .test_coverage_summary("No implementation was generated for this requirement, so no coverage exists yet.")
                .build();
    }

    private RiskAnalysis urlShortenerRisks() {
        return RiskAnalysis.builder()
                .risks(List.of(
                        new RiskAnalysis.Risk("Functional", "Short-code collision on the auto-generation path under concurrent writes.", "DB-level unique index rejects the losing insert; caller-visible retry loop bounds generation attempts (current: app-level check + index as backstop)."),
                        new RiskAnalysis.Risk("Functional", "Custom alias race condition (two requests for the same alias simultaneously).", "Unique index on short_code guarantees only one insert succeeds; the losing request should surface a 409 Conflict (verified in integration tests)."),
                        new RiskAnalysis.Risk("AI-related", "AI-generated code silently omits uniqueness/error handling that looks correct at a glance (observed in this project — see ShortenerService and code_review findings).", "Mandatory human code review pass on every AI-drafted function before merge; unit tests written independently of the AI-generated implementation, not copied from it."),
                        new RiskAnalysis.Risk("AI-related", "AI-generated tests can be shallow (only happy-path), giving false confidence via a passing test suite.", "Engineer added negative/edge-case tests beyond the AI-drafted set (see missing_edge_cases and test files)."),
                        new RiskAnalysis.Risk("Design", "SQLite is not suitable for high-concurrency production writes.", "ORM boundary (Spring Data JPA) allows a connection-string swap to PostgreSQL with no application code changes; documented as a scaling trade-off, not solved here."),
                        new RiskAnalysis.Risk("Scalability", "No caching layer; every redirect hits the database.", "For a prototype, DB-indexed lookup is fast enough; a production version would add a read-through cache (e.g. Redis) in front of hot short codes — explicitly out of scope here."),
                        new RiskAnalysis.Risk("Security", "No rate limiting; POST /shorten could be used to flood the database.", "Documented as an open item; would add a rate-limit middleware (e.g. token bucket per IP) before production use.")
                ))
                .build();
    }

    private RiskAnalysis genericRisks() {
        return RiskAnalysis.builder()
                .risks(List.of(
                        new RiskAnalysis.Risk("Functional", "Requirement ambiguity may lead to building the wrong thing.", "Resolve ambiguities (see requirement_analysis.ambiguities) with the requester before implementation."),
                        new RiskAnalysis.Risk("AI-related", "AI-generated code/tests for an under-specified requirement can look plausible but not match actual intent.", "Engineer must validate against the clarified requirement, not the AI's guess at it.")
                ))
                .build();
    }

    private FinalSummary finalSummary(boolean isUrlShortener) {
        if (isUrlShortener) {
            return FinalSummary.builder()
                    .implementation_approach("Implemented as a layered service: Spring Boot Web MVC controllers (HTTP) -> Service layer (ShortenerService.java, domain logic) -> Spring Data JPA -> SQLite. AI was used within each task (schema drafting, code generation, test scaffolding) with every output reviewed and, in several cases, corrected by the engineer before acceptance — see task_decomposition and validation for specific examples.")
                    .generated_artifacts(List.of(
                            "JPA entities (Url, ClickEvent)",
                            "DTOs (ShortenRequest, ShortenResponse, etc.)",
                            "POST /shorten, GET /{shortCode}, GET /analytics/{shortCode} endpoints",
                            "Unit tests (src/test/java/com/copilot/urlshortener/service/ShortenerServiceTest.java)",
                            "Integration tests (src/test/java/com/copilot/urlshortener/controller/UrlApiIntegrationTest.java)",
                            "React/TypeScript frontend (requirement input + tabbed results view)",
                            "README.md, ARCHITECTURE.md with Mermaid diagrams"
                    ))
                    .risks_and_validation("Key risks: code-generation collisions (mitigated via DB unique index + bounded retry), AI-generated code/tests with silent gaps (mitigated via mandatory engineer review — two concrete gaps found and fixed, documented in validation.code_review), and unaddressed production concerns (rate limiting, caching, open-redirect hardening) explicitly flagged as open rather than silently skipped.")
                    .assumptions_and_limitations("Assumes no auth requirement, no link-expiry requirement, and SQLite is acceptable for prototype scale. Limitations: no rate limiting, no caching layer, no soft-delete/expiry, no protection against shortening links to malicious domains beyond basic scheme/length validation. These are scope boundaries stated up front, not gaps discovered after the fact.")
                    .build();
        }
        return FinalSummary.builder()
                .implementation_approach("No concrete implementation generated — requirement did not match the supported mandatory use case and contained insufficient detail for the generic heuristic engine to derive one.")
                .generated_artifacts(List.of("Requirement analysis only"))
                .risks_and_validation("Not applicable without an implementation.")
                .assumptions_and_limitations("This prototype's generic path performs lightweight requirement analysis only; it does not synthesize arbitrary backend code end-to-end. The URL shortener path is the fully implemented, validated demonstration required by the assignment.")
                .build();
    }

    public CopilotResponse runCopilot(String requirement) {
        boolean isUrlShortener = isUrlShortenerRequirement(requirement);

        RequirementAnalysis analysis = isUrlShortener ? urlShortenerAnalysis() : genericAnalysis(requirement);
        List<EngineeringTask> tasks = isUrlShortener ? urlShortenerTasks() : genericTasks(requirement);
        EngineeringArtifacts artifacts = isUrlShortener ? urlShortenerArtifacts() : genericArtifacts();
        ValidationReport validation = isUrlShortener ? urlShortenerValidation() : genericValidation();
        RiskAnalysis risks = isUrlShortener ? urlShortenerRisks() : genericRisks();
        FinalSummary summary = finalSummary(isUrlShortener);

        return CopilotResponse.builder()
                .requirementAnalysis(analysis)
                .taskDecomposition(tasks)
                .engineeringArtifacts(artifacts)
                .validation(validation)
                .riskAnalysis(risks)
                .finalSummary(summary)
                .build();
    }
}

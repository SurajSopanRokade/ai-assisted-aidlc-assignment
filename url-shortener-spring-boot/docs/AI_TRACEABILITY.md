# AI traceability log

How AI was used, what it produced, what was rejected, and how the output was
validated. The governing rule for this project:

> **AI drafts. The engineer decides. A decision is not final until a test or a
> live probe proves it.**

## A note on evidence quality

Entries carry one of two labels, because they are not equally verifiable and
pretending otherwise would undermine the point of the document:

- **[VERIFIED]** — the defect, the decision and the proof are all in the
  repository: a probe that can be re-run, a regression test, a commit.
- **[RECORDED]** — prompt transcripts were not retained, so the entry is
  reconstructed from git history, code comments and notes written at the time.
  Reported as recollection, not as transcript.

Labelling the second group honestly is deliberate. The most damaging artefact
an AI-assisted project can produce is a confident, unverifiable claim that
happens to be false (see AI-07). Presenting reconstructed notes as verbatim
prompt logs would be that same failure in a new place.

---

## AI-01 — Data model [RECORDED]

Drafted JPA entities and DTOs from a field list.

**Engineer changes:** added the unique index on `short_code`; introduced
`click_events` in place of a bare counter column. A counter alone forecloses
every time-based analytics question, and the data to answer one later is not
recoverable if it was never recorded.

## AI-02 — Short-code allocation [RECORDED]

Drafted a random generator with no uniqueness check and an unbounded retry loop.

**Engineer changes:** added the existence check, bounded the loop to 5 attempts,
and made exhaustion a typed exception mapped to 503. The bound is asserted
directly (`verify(..., times(5))`) so it cannot silently drift.

## AI-03 — Redirect semantics [RECORDED]

Draft used **301 Moved Permanently**.

**Rejected.** A permanent redirect is cached by browsers and intermediaries,
which freezes the destination and silently stops click counting — it would
break the analytics requirement without producing an error anywhere. Changed to
307, with `Cache-Control: no-store` for the same reason.

## AI-04 — Tests [RECORDED]

Drafted happy-path cases only.

**Engineer additions:** retry exhaustion, duplicate alias, never-clicked
analytics, and both expiry boundaries. Sleep-based timing replaced with an
injected `Clock`, which is what makes the boundary assertions exact rather than
flaky.

## AI-05 — `short_url` construction [RECORDED]

Draft built the response URL from `HttpServletRequest.getServerName()`.

**Accepted at the time, then found to be Host-header injection**: a request
carrying `Host: evil.example.com` gets back a link advertising
`http://evil.example.com:80/…`. Replaced with configuration (`app.base-url`).

Recorded as accepted-then-corrected rather than rewritten as a clean rejection,
because that is what happened, and the sequence is the interesting part: it
passed review and only surfaced when the running service was probed.

## AI-06 — Test isolation [RECORDED]

Draft integration test called `deleteAll()` against whatever datasource was
configured, which truncates a developer's local database when run outside CI.

**Engineer changes:** a dedicated test profile, pinned by the surefire
configuration so it cannot be bypassed by forgetting a flag.

## AI-07 — Error contract [VERIFIED]

**Status: rejected AI output, and rejected an AI-written claim about it.**

A machine-readable validation report asserted:

> *"UrlController.java — AI draft returned raw exception messages to clients;
> replaced with explicit Exception Handler status codes and sanitized details.
> **Low — fixed**"*

It was not fixed. A live probe against the running jar:

```
POST /shorten {"original_url":"https://a.com","expires_at":"not-a-date"}
→ 500 {"detail":"Internal server error: JSON parse error: Cannot deserialize
   value of type `java.time.LocalDateTime` ... at index 0"}
```

`GlobalExceptionHandler` still contained
`"Internal server error: " + ex.getMessage()`.

**Why this is the most important entry in the log.** The claim was generated,
sounded plausible, was never checked against the code, and was then presented as
evidence of diligence. A reviewer who trusted it would have stopped looking at
exactly the wrong moment. AI did not cause the bug; accepting an AI-written
*self-assessment* without verification is what let it survive.

**Response — three layers, because the process failed, not just the code:**

1. Fixed the handler: generic message plus an `error_id` correlating to the log.
2. `ErrorContractIntegrationTest` scans response bodies against a
   forbidden-fragment list (`exception`, `org.springframework`, `select `,
   `insert `, `sqlite`, `jdbc`, …).
3. `CopilotReportAccuracyTest` pins every claim in the report to the mechanism
   that backs it, so removing a mitigation while leaving the claim fails the
   build.

**AI's actual useful contribution here:** enumerating which Spring exceptions
bypass `@RestControllerAdvice`. The list was incomplete — see AI-09, where the
gap it missed cost a real bug — so it was checked against the framework and
against live requests rather than trusted.

## AI-08 — Concurrency [VERIFIED]

**Status: alternative considered and rejected on technical grounds.**

Load probe on the running service:

```
100 concurrent GET /{shortCode}
→ 20 × 307, 80 × 500 ([SQLITE_BUSY] The database file is locked)
→ analytics reported click_count = 20
```

Two independent defects: lock contention, and lost updates from
read-modify-write on the entity.

**Rejected: `synchronized` on the service method.** The obvious fix, and wrong
in an instructive way — it would make the symptom *rarer* in a single-instance
test while leaving the defect in place, because the race is in the database, not
the JVM. It fails the moment a second replica exists, and by then the test that
would have caught it is green.

**Rejected: `@Async` click recording.** Correct at scale, and recorded in
LIMITATIONS.md as the next step. Rejected here because it makes analytics
eventually consistent — a semantic change nobody asked for — and would make the
regression test non-deterministic.

**Accepted:** atomic SQL increment (`set clickCount = clickCount + 1`), WAL
journaling, 5s busy timeout, single-connection pool so writers queue fairly.

**Validation:** the regression test was written **first** and confirmed failing
at 20/100. It now asserts exactly 100 successes and exactly 100 clicks. Exact,
not approximate — a tolerance would have accepted the original behaviour.

## AI-09 — Rate limiter [VERIFIED]

**Status: accepted after substantial correction.**

The generated filter ran and passed a naive test. Three problems:

1. **Keyed on `X-Forwarded-For`.** Client-supplied, so an attacker bypasses the
   limit entirely by rotating the header. Changed to the socket address, with
   the proxy configuration that makes that correct documented in the class.
2. **Unbounded counter map.** A source cycling through addresses turns the
   limiter into a memory-exhaustion vector — the control becomes the
   vulnerability. Added bounded eviction.
3. **Threw from inside the filter.** This is the gap in AI-07's exception list:
   a filter runs outside the `DispatcherServlet`, so the exception bypassed
   `@RestControllerAdvice` and produced the container's default error page
   instead of the project's error envelope. Fixed by delegating to
   `HandlerExceptionResolver`.

Defect 3 is the clearest example of AI output that is locally correct and
globally wrong: the code compiles, the logic is right, and it silently breaks a
cross-cutting contract established elsewhere in the codebase.

**Validation:** `RateLimitFilterIntegrationTest` asserts the 429 carries both a
`Retry-After` header and the standard `detail` envelope.

## AI-10 — Documentation [VERIFIED]

**Status: partially rejected.**

Drafted prose asserted behaviour that was aspirational rather than shipped.
Corrections made:

- "structured logging" — it was plain-text SLF4J. Either implement it or stop
  claiming it; correlation IDs were added and the claim narrowed to what exists.
- Draft task notes attributed proposals to AI that could not be substantiated
  ("AI proposed a synchronized block"). Rewritten to what is defensible:
  *considered and rejected*, with the technical reason.
- Coverage described as "estimated high" replaced with JaCoCo output and a build
  gate — which immediately surfaced `CopilotEngineService` at 10.8% line
  coverage, a path with no tests at all.

## AI-11 — Observability [VERIFIED]

**Status: my own error, caught the same way as the others.**

`prometheus` was added to `management.endpoints.web.exposure.include` and a
scrape endpoint was written into the README and the architecture doc. It did not
exist: exposing an endpoint in configuration does nothing without a registry
implementation on the classpath, and `micrometer-registry-prometheus` was
missing. `GET /actuator/prometheus` returned 404.

Same failure mode as AI-07 — a plausible claim about one's own work, written
down without checking — which is worth recording precisely because it shows the
discipline is not a personality trait. It is the probe.

**Fixed:** added the registry, and `ActuatorEndpointsIntegrationTest` so the
claim is pinned by a test rather than by intention.

**Secondary finding, worth knowing:** the first version of that test failed
against a perfectly healthy application. Spring Boot disables metrics *export*
auto-configuration in tests by default, so the endpoint is absent from a test
context even when it works at runtime; `@AutoConfigureObservability` restores
it. A test that fails against working code is worse than no test — it teaches
people to delete tests.

---

## What AI was good and bad at, on this project

**Good:** boilerplate with a known shape (DTOs, repositories, Mermaid syntax,
OpenAPI annotations); enumerating candidates to check; first-draft prose;
recalling framework APIs. It genuinely compressed the mechanical work.

**Bad, in a specific and consistent way:** anything whose correctness depends on
context outside the file being edited. Every significant defect here was of that
kind — concurrency (correctness lives in the database), the error contract
(correctness lives in a cross-cutting handler), the rate limiter (correctness
lives in the filter chain), Host trust (correctness lives in the threat model).
The generated code was locally reasonable in every case.

**The practice that actually caught things:** running the service and attacking
it. Code review found none of these. A green test suite found none of them.
Every one came from driving the real API with adversarial input. Static review
and AI review share a blind spot, and it is the same blind spot.

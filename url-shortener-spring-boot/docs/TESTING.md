# Testing approach

## Principle

A test exists to make a specific failure impossible to reintroduce silently. If
a test would still pass with the defect present, it is documentation, not a
test.

Concretely: every defect fixed here has a test that **fails without the fix**.
The concurrency test is the clearest case — it asserts exactly 100 successes and
exactly 100 recorded clicks, against an implementation that produced 20 and 20.

## Running

```bash
./mvnw test        # 71 tests
./mvnw verify      # tests + JaCoCo report + coverage gate
```

No global Maven install needed — the wrapper is committed. Tests run under the
`test` profile, pinned by the surefire configuration in `pom.xml`, so they use
`target/test-url-shortener.db` and cannot touch a developer's local database.

Coverage report: `target/site/jacoco/index.html`.

## Layers

| Layer | Count | What it proves | Cost |
| --- | --- | --- | --- |
| Unit (`ShortenerServiceTest`) | 17 | Domain logic in isolation: allocation, collisions, expiry boundaries, analytics, metrics | Milliseconds; mocked repositories |
| Unit (`RequestIdFilterTest`) | 7 | Correlation-id sanitisation, including log-injection stripping | Milliseconds |
| Heuristics (`CopilotEngineServiceTest`) | 12 | Requirement triage and routing | Milliseconds |
| Report accuracy (`CopilotReportAccuracyTest`) | 10 | Documentation claims match the code | Milliseconds; reads source files |
| HTTP contract (`UrlApiIntegrationTest`) | 11 | Status codes, JSON shape, headers through the full stack | Seconds; MockMvc |
| Error contract (`ErrorContractIntegrationTest`) | 7 | No response leaks internals; client faults are 4xx | Seconds; MockMvc |
| Operational surface (`ActuatorEndpointsIntegrationTest`) | 4 | Health, Prometheus scrape, tagged metrics, OpenAPI contract | Seconds; MockMvc |
| Rate limiting (`RateLimitFilterIntegrationTest`) | 2 | 429 behaviour and envelope | Seconds; MockMvc |
| Concurrency (`RedirectConcurrencyIntegrationTest`) | 2 | Exactness under 100 parallel requests | Seconds; real Tomcat |

## Decisions worth explaining

**SQLite in tests, not H2.** H2 is faster and cleaner, but the behaviour most
worth testing here — WAL journaling, single-writer locking, `SQLITE_BUSY` — is
SQLite-specific. On H2 the concurrency test would pass without proving anything
about production.

**A real container for the concurrency test.** MockMvc does not run a servlet
container; its "concurrent" requests do not contend for connections the way real
ones do. `RANDOM_PORT` plus a thread pool is the only way that test means
something.

**Injected `Clock` instead of sleeps.** Expiry boundaries are asserted exactly:
expiry equal to now is live, one nanosecond earlier is expired. A sleep-based
test cannot make that assertion and would be flaky under load.

**Exact assertions on concurrency.** `assertEquals(100, clickCount)`, not
`assertTrue(clickCount > 90)`. A tolerance would have accepted the original
defect (20/100 is not obviously wrong if you allow "roughly").

**Testing the documentation.** A validation report claiming a security fix that
was never made is worse than no report — it stops the next reader looking.
`CopilotReportAccuracyTest` cannot verify prose, but it verifies that the
*mechanism* each claim depends on still exists — the atomic increment, the
absence of `ex.getMessage()` in the catch-all, the base URL coming from config.

**Testing the operational surface too.** `ActuatorEndpointsIntegrationTest`
exists for the same reason: `/actuator/prometheus` was documented as available
while returning 404, because exposing an endpoint in configuration does nothing
without a registry on the classpath. A claim about observability needs a test as
much as a claim about behaviour does.

Note the `@AutoConfigureObservability` on that class — Spring Boot disables
metrics export in tests by default, so without it the test fails against a
working application. A test that fails against correct code gets deleted rather
than fixed, which is a worse outcome than not having written it.

## Coverage, measured

| Class | Branch | Line |
| --- | --- | --- |
| `ShortenerService` | 100% | 100% |
| `RequestIdFilter` | 100% | 100% |
| `CopilotEngineService` | 75% | 96.9% |
| `RateLimitFilter` | 60% | 83.3% |
| `GlobalExceptionHandler` | 62.5% | 67.3% |

A JaCoCo rule bound to `verify` fails the build if `ShortenerService` branch
coverage drops below 80%. The gate is on the domain service specifically —
blanket project-wide thresholds tend to be satisfied by testing whatever is
easiest rather than whatever matters.

The two lowest numbers are honest: `RateLimitFilter`'s uncovered branches are
the eviction path (needs 10,000 clients in one window) and
`GlobalExceptionHandler`'s are defensive fallbacks reachable only when another
filter fails. Both are recorded in [LIMITATIONS.md](LIMITATIONS.md).

## What is not tested

Stated so the coverage numbers are not read as more than they are:

- **Sustained load.** Burst concurrency is proven; throughput is not measured
  and no QPS figure is claimed anywhere.
- **Multi-instance behaviour.** Rate limiting is per-instance and expiry uses
  each instance's clock; neither is exercised across nodes.
- **The frontend.** Typechecked and linted, no automated tests.
- **The 2048-character boundary** on `original_url` — enforced by annotation,
  no off-by-one test.
- **Redirect loops** where one short link targets another on the same service.

## Manual verification

Automated tests cover regressions. These probes are what *found* the defects in
the first place, and are worth re-running after any change to the write path:

```bash
# Client error must be 400 with no internals
curl -i -X POST localhost:8080/shorten -H 'Content-Type: application/json' \
  -d '{"original_url":"https://a.com","expires_at":"not-a-date"}'

# short_url must ignore a forged Host header
curl -s -X POST localhost:8080/shorten -H 'Host: evil.example.com' \
  -H 'Content-Type: application/json' -d '{"original_url":"https://b.com"}'

# Every redirect must succeed and every click must be counted
CODE=$(curl -s -X POST localhost:8080/shorten -H 'Content-Type: application/json' \
  -d '{"original_url":"https://example.com"}' | jq -r .short_code)
seq 1 100 | xargs -P 16 -I{} curl -s -o /dev/null -w '%{http_code}\n' \
  "localhost:8080/$CODE" | sort | uniq -c
curl -s "localhost:8080/analytics/$CODE" | jq .click_count   # must be 100
```

# Limitations and known gaps

Everything here is a deliberate scope boundary or a known defect, stated before
anyone has to find it. Nothing in this file is described as solved.

Severity is *impact if this were deployed as-is*, not effort to fix.

---

## Critical — blocks production deployment

### 1. No authentication or authorization
Every endpoint is public. Anyone can create links; anyone with a short code can
read its analytics. There is no concept of an owner.

**Why not done:** the assignment centres on AI-assisted engineering execution,
and auth would have consumed the time that went into the concurrency and error
contract work. This was a scope decision, not an oversight.

**Fix:** API keys or an OAuth2 resource server, plus an `owner_id` column on
`urls` and an ownership check in `getAnalytics`.

### 2. No schema migrations
The prod profile runs `ddl-auto=validate`, which correctly refuses to mutate a
production schema — but nothing creates it. First deployment needs one manual
run with `APP_DDL_AUTO=update`.

**Fix:** Flyway. Baseline migration from the current entities, then
`validate` unconditionally.

---

## High — correctness or capacity ceilings

### 3. Write throughput is bounded by SQLite's single writer
Every redirect writes twice (event insert, counter update). WAL plus a busy
timeout plus a single-connection pool makes contention *safe* — writers queue
instead of failing — but it does not make it *fast*. Concurrent writes are
serialised.

**Measured:** 100 concurrent redirects all succeed with exact counts. No
sustained-load figure exists; there is no load test, and no QPS is claimed
anywhere in this project.

**Fix:** PostgreSQL. The JPA boundary means this is a connection string plus
deleting the pool-size pin.

### 4. Rate limiting is per instance
Counters are in-memory. Behind N replicas the effective limit is N x the
configured value.

**Fix:** shared counters in Redis, or enforce at the gateway.

### 5. Open redirect by design
The service redirects to arbitrary caller-supplied destinations. Scheme and
length are validated; reputation is not checked. A short link can lend
credibility to a malicious destination.

**Partial mitigation:** `http`/`https` only, 2048-character cap.
**Fix:** a domain reputation feed plus an interstitial warning for unknown
destinations.

---

## Medium

### 6. Click recording is synchronous
The event insert and counter update happen inside the redirect request, adding
write latency to the hot path.

**Fix:** batched asynchronous writer. Deliberately not done now — it makes
analytics eventually consistent, which is a semantic change nobody asked for.

### 7. No caching layer
Every redirect reaches the database.

**Fix:** read-through cache on hot short codes, invalidated on expiry.

### 8. No dependency vulnerability scanning
No CVE scan of the dependency tree in the build.

**Fix:** OWASP dependency-check in CI, plus Dependabot.

### 9. Expiry uses server wall-clock time
`Clock` is injected, so the boundary is tested deterministically — but across
instances, skew could make a link live on one node and expired on another.

**Fix:** store instants in UTC and compare against a monotonic source.

### 10. No frontend tests
The React app has no automated tests. It is typechecked (`tsc -b`) and linted,
and its API types mirror the backend DTOs by hand — so a backend contract change
is caught at compile time only if the types are updated with it.

**Fix:** Vitest plus Testing Library for the components; generate the TS types
from the OpenAPI contract to remove the manual mirroring.

---

## Low

### 11. `GlobalExceptionHandler` branch coverage is 62.5%
The uncovered branches are defensive fallbacks that need a failure inside
another filter to reach.

### 11b. Metrics export is disabled in tests by default
`ActuatorEndpointsIntegrationTest` needs `@AutoConfigureObservability` to see
the Prometheus endpoint. Any future test asserting on metrics needs the same
annotation, or it will fail against a working application.

### 12. `RateLimitFilter` eviction path is untested
Requires 10,000 distinct clients in one window to exercise.

### 13. No load or soak testing
Burst concurrency is covered; sustained load and memory behaviour over time are
not.

### 14. Single-node deployment only
No horizontal scaling story: SQLite is file-local and rate-limit state is
in-process. Items 3 and 4 are the two things that must change first.

---

## Explicitly out of scope

Not gaps — decisions. Listed so their absence reads as intent:

- Link deletion and editing
- Custom domains
- QR code generation
- Bulk import
- Geographic, referrer and device analytics — the `click_events` table exists
  precisely so these are additive rather than a migration
- Password-protected links

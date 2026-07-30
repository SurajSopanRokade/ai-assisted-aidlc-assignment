# Scenario 2 — Brownfield: harden the running service

**Type:** enhancement, refactor and bug fix on an existing codebase
**Approach:** probe the running service adversarially rather than only reading
it, then fix what the probes expose.

---

## 1. How the work was found

The service passed its tests and read as correct. Starting the jar and driving
the real API is where every defect below came from — all four were invisible to
both code review and the test suite.

```
# Probe 1 — a client typo
POST /shorten {"original_url":"https://a.com","expires_at":"not-a-date"}
→ 500 {"detail":"Internal server error: JSON parse error: Cannot deserialize
   value of type `java.time.LocalDateTime` ... DateTimeParseException ..."}

# Probe 2 — a forged Host header
POST /shorten, Host: evil.example.com
→ 201 {"short_url":"http://evil.example.com:80/faJx3yT"}

# Probe 3 — 100 concurrent redirects on one code
→ 20 x 307, 80 x 500 {"detail":"... [SQLITE_BUSY] The database file is locked
   ... [insert into click_events (clicked_at,url_id) values (?,?)]"}
→ analytics reported click_count = 20
```

## 2. Impact analysis before touching anything

| Defect | Blast radius | Modules touched |
| --- | --- | --- |
| D1 Internal detail in responses | Every error path; discloses schema and framework internals (CWE-209) | `GlobalExceptionHandler`, `ErrorResponse`, frontend error handling |
| D2 Client faults reported as 500 | Error-rate alerting is meaningless; a typo pages an on-call | `GlobalExceptionHandler` |
| D3 Host header trusted | Any caller can mint links on a domain they chose | `UrlController`, new `AppProperties`, all deploy config |
| D4 Lost updates + lock failures | Analytics silently wrong; hot path fails under load | `ShortenerService`, `UrlRepository`, datasource config |

D3 is the one with the widest reach: removing the Host dependency means the base
URL becomes configuration, which touches the prod profile, compose, the
Dockerfile and the test profile. That sequencing mattered — config plumbing
first, then the controller change, so the service was never in a state where it
had no base URL at all.

## 3. Fixes, and the alternatives rejected

**D1/D2 — error contract.** The catch-all now returns a fixed string plus an
`error_id`; the cause goes to the log under that same id. Added a handler for
`HttpMessageNotReadableException` (400) and `DataIntegrityViolationException`
(409, the alias race loser).

> *Rejected:* keeping the message for 4xx only. Attractive, but the classifier
> for "is this message safe" is the exception type, and the 500 path is exactly
> where the type is unknown. A rule that depends on knowing what you do not know
> is not a rule.

**D3 — base URL.** Now `app.base-url`, validated at startup.

> *Rejected:* an allowlist of permitted Host values. It preserves multi-domain
> support but keeps a client-controlled value on the trust path, and every
> deployment then needs the list maintained. Configuration is one value and no
> trust decision.

**D4 — concurrency.** Two independent causes, two fixes:
- *Lost updates:* read-modify-write on the entity replaced with
  `update Url set clickCount = clickCount + 1 where id = ?`.
- *SQLITE_BUSY:* WAL journaling, a 5s busy timeout, and a single-connection pool
  so writers queue fairly instead of racing for the lock.

> *Rejected:* a `synchronized` block on the service method. It looks like a fix
> and would make the symptom rarer in a single-instance test, which is worse
> than leaving it visible — it serialises inside one JVM while the actual race
> is in the database, so it fails the moment a second replica exists.
>
> *Rejected:* moving click recording to `@Async`. It genuinely fixes hot-path
> latency and is the right answer at scale, but it makes analytics eventually
> consistent, which the assignment never asked for, and it would have made the
> regression test non-deterministic. Recorded in LIMITATIONS.md as the next step.

## 4. Refactors carried alongside

Not defects, but the fixes made them obvious:

- `resolveAndRecordClick` returned a `Url` whose in-memory `clickCount` was
  stale the moment the increment became atomic. It now returns the destination
  — the only thing the caller ever needed.
- `getAnalytics` loaded every click event to compute one maximum. Replaced with
  a `max()` aggregate: cost is now constant in click volume, where before it
  degraded worst on the most popular links.
- `RedirectView` replaced with an explicit `ResponseEntity`, so a
  caller-supplied destination is not put through the view resolver's URL
  processing.
- CORS moved out of the bootstrap class into `WebConfig`, origins from config.

## 5. Safe change management

Each defect got its regression test **first**, confirmed failing, before the fix
landed. Writing the test second proves only that the code does what it does; the
concurrency test is the clearest case, asserting exactly 100 successes and
exactly 100 clicks against an implementation that produced 20 and 20.

The wider guard is `CopilotReportAccuracyTest`. The validation report is exactly
the kind of artefact that goes stale invisibly — a security finding marked
*fixed* while the code still exhibits it costs more than no report at all,
because it stops the next reader looking. So the report lives in a versioned
resource and each claim is pinned to the mechanism that backs it: removing a
mitigation while leaving the claim in place fails the build.

## 6. Result

| Behaviour | Unfixed | Now |
| --- | --- | --- |
| 100 concurrent redirects | 20 ok / 80 × 500 | 100 ok / 0 errors |
| Recorded clicks for 100 | 20 | 100 |
| Malformed date | 500 + internals | 400, generic |
| `short_url` under forged Host | attacker-chosen | configured value |
| Past-dated expiry | accepted | 422 |
| `ShortenerService` branch coverage | not measured | 100%, gated at 80% |

71 tests now cover the service, including a regression test for each row above.

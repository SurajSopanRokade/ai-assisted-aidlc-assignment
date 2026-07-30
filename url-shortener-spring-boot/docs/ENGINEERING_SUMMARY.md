# Final engineering summary

## What was built

A URL shortener with create, redirect, analytics and expiry, plus the
reliability and security work needed to make those correct rather than merely
present. Spring Boot 3.2 on Java 17, SQLite behind JPA, React and TypeScript
frontend, 71 automated tests with a coverage gate, containerised.

## Plan and rationale

The requirement named "core APIs, analytics, and reliability features" and
defined none of them. Each was turned into something acceptance-testable before
implementation — the reasoning is in
[scenarios/01-greenfield.md](scenarios/01-greenfield.md), and the harder case,
where the requirement was pure adjectives, is in
[scenarios/03-ambiguous.md](scenarios/03-ambiguous.md).

Work was sequenced by dependency: data model, then allocation, then endpoints,
then tests, then the cross-cutting concerns that could only be designed once the
request path existed. The full task graph with per-task AI attribution is served
by `POST /copilot/analyze` and mirrored in
[AI_TRACEABILITY.md](AI_TRACEABILITY.md).

## Artefacts

- REST API with an OpenAPI 3 contract generated from the controllers
- Domain service with no HTTP or static-time dependencies
- Atomic click accounting, WAL-configured datasource
- Single error contract: typed exceptions, no internal disclosure, correlation IDs
- Fixed-window rate limiter on the write path
- Micrometer business metrics and a Prometheus endpoint
- 71 tests including concurrency and error-contract regressions; JaCoCo gate
- Non-root container with a healthcheck, working volume, a real prod profile
- CI running backend, frontend and image build
- Architecture, three scenarios, AI traceability, testing approach, limitations

## Risks, trade-offs, validation

The defects that mattered were **not** found by reading code. A green test suite
and a clean review missed all four; every one came from starting the service and
probing it:

| Defect | Evidence | Now |
| --- | --- | --- |
| Concurrent redirects failing | 80 of 100 returned `SQLITE_BUSY` 500s | 100/100 succeed |
| Lost click updates | 100 requests recorded 20 clicks | Exactly 100 |
| Internal disclosure | Raw `INSERT` and Jackson internals in responses | Generic message + `error_id` |
| Host header injection | `Host: evil.example.com` → attacker-chosen `short_url` | From configuration |

That is the transferable finding of this project: static review and AI review
share a blind spot, and it covers exactly the class of defect that survives to
production — anything whose correctness depends on context outside the file
being edited.

Trade-offs taken deliberately: exact click counts over hot-path latency;
uniqueness checking over shorter enumerable codes; a write-throughput ceiling
over the complexity of a second datastore at prototype scale.

## Assumptions

Single instance. No authentication requirement. Prototype-scale traffic. Server
wall-clock time for expiry. SQLite acceptable, with the JPA boundary kept clean
so PostgreSQL is a configuration change.

## Limitations

The full register is [LIMITATIONS.md](LIMITATIONS.md). The five that would block
deployment:

1. No authentication or authorization on any endpoint
2. No schema migration tool; first prod boot needs a manual step
3. Write throughput bounded by SQLite's single writer
4. Rate limiting is per-instance and does not survive replication
5. Open redirect by design — destinations are not reputation-checked

## What I would do next, in order

1. **Auth.** It is the difference between a demo and a service.
2. **PostgreSQL and Flyway.** Removes the write ceiling and the manual schema step together.
3. **Async batched click writes.** Hot-path latency, once eventual consistency in analytics is agreed.
4. **Redis-backed rate limiting.** Prerequisite for more than one replica.
5. **Frontend tests and OpenAPI-generated types.** Removes the hand-mirrored DTOs.

## What I would tell the next engineer

Run the thing and attack it before trusting it. Every hour spent probing the
running service found something; the hours spent re-reading the code found
nothing that the tests had not already covered.

And treat generated self-assessment as a claim, not as evidence. The most
dangerous artefact in an AI-assisted project is not broken code — it is a
confident, well-formatted validation report asserting a security fix that was
never made. Broken code eventually announces itself; a plausible false claim
does the opposite, because everyone downstream stops checking. That is why the
report here is pinned by a test that fails the moment a claim stops being true.

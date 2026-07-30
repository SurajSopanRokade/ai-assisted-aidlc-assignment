# URL Shortener

A URL shortener built as an exercise in AI-assisted engineering execution: core
API, click analytics, link expiry, and the reliability work needed to make those
correct under concurrent load.

The engineering narrative — what was ambiguous, what was decided, what AI got
wrong, what remains broken — is in [`docs/`](docs/) and is the point of the
project as much as the service is.

---

## Quick start

**Requirements:** Java 17+. No Maven install needed (wrapper is committed).
Node 20+ for the frontend. Docker optional.

```bash
cd url-shortener-spring-boot
./mvnw spring-boot:run          # http://localhost:8080
```

```bash
cd frontend
npm install && npm run dev      # http://localhost:3000
```

Or containerised:

```bash
docker compose up --build
```

Verify:

```bash
curl -X POST localhost:8080/shorten \
  -H 'Content-Type: application/json' \
  -d '{"original_url":"https://example.com"}'
```

## API

Interactive contract at `/swagger-ui.html`, raw OpenAPI at `/v3/api-docs` —
generated from the controllers, so it cannot drift from the code.

| Method | Path | Purpose |
| --- | --- | --- |
| `POST` | `/shorten` | Create a short link. Optional `custom_alias`, `expires_at`. |
| `GET` | `/{shortCode}` | 307 to the destination; records a click. |
| `GET` | `/analytics/{shortCode}` | Click count, timestamps, expiry state. |
| `POST` | `/copilot/analyze` | Engineering report for a requirement. |
| `GET` | `/actuator/health` `/metrics` `/prometheus` | Operational endpoints. |

**Error contract.** Every non-2xx returns the same shape. `detail` is always
safe to show a user; 5xx additionally carries `error_id`, which correlates to
the server log entry holding the real cause. Validation failures carry
`field_errors`.

```json
{ "detail": "Validation failed", "field_errors": { "expiresAt": "expires_at must be in the future" } }
```

| Status | When |
| --- | --- |
| 400 | Malformed body — invalid JSON, unparseable date |
| 404 / 410 | Unknown short code / link expired |
| 409 | Custom alias already taken |
| 422 | Validation failed |
| 429 | Rate limit exceeded (`Retry-After` included) |
| 503 | Short-code allocation exhausted its retry budget |

## Configuration

| Variable | Default | Notes |
| --- | --- | --- |
| `APP_BASE_URL` | `http://localhost:8080` | Public origin used to build `short_url`. **Set this in any real deployment** — see below. |
| `APP_CORS_ORIGINS` | `http://localhost:3000` | Exact origins; no wildcards. |
| `APP_DB_PATH` | `url_shortener.db` | SQLite file. |
| `APP_DDL_AUTO` | `validate` under `prod` | First prod boot needs `update`; no migration tool yet. |
| `SPRING_PROFILES_ACTIVE` | — | `prod` for deployment. |

`APP_BASE_URL` is configuration rather than being read from the request's `Host`
header on purpose: `Host` is client-controlled, and deriving links from it let a
caller mint links pointing at a domain they chose.

## Testing

```bash
./mvnw verify    # 71 tests + coverage gate
```

Approach, layer breakdown, measured coverage and what is deliberately not
tested: [`docs/TESTING.md`](docs/TESTING.md).

## Documentation

| Document | What it covers |
| --- | --- |
| [docs/ENGINEERING_SUMMARY.md](docs/ENGINEERING_SUMMARY.md) | **Start here** — plan, artefacts, risks, limitations |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Components, request flows, key decisions |
| [docs/AI_TRACEABILITY.md](docs/AI_TRACEABILITY.md) | How AI was used, what was rejected and why |
| [docs/scenarios/01-greenfield.md](docs/scenarios/01-greenfield.md) | Building from nothing |
| [docs/scenarios/02-brownfield.md](docs/scenarios/02-brownfield.md) | Hardening an existing codebase: four defects found and fixed |
| [docs/scenarios/03-ambiguous.md](docs/scenarios/03-ambiguous.md) | Resolving an under-specified requirement |
| [docs/TESTING.md](docs/TESTING.md) | Testing strategy and coverage |
| [docs/LIMITATIONS.md](docs/LIMITATIONS.md) | Known gaps, severity-ranked |

## Status, honestly

**Works and is proven:** the full create/redirect/measure loop; exact click
accounting under 100 concurrent requests; an error contract that leaks nothing
and never reports a client mistake as a server fault; link expiry with tested
boundaries; correlation IDs and business metrics.

**Known gaps** — full register in [LIMITATIONS.md](docs/LIMITATIONS.md):

- No authentication. Every endpoint is public.
- No schema migrations; first prod boot needs a manual step.
- Write throughput is bounded by SQLite's single writer. No QPS is claimed.
- Rate limiting is per-instance, so it does not survive horizontal scaling.
- Open redirect by design — destinations are not reputation-checked.

None of these is described anywhere in this repository as solved.

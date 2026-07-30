# Architecture

## System context

```mermaid
graph TD
    Browser[Browser / API consumer]
    UI[React + TypeScript UI<br/>Vite, port 3000]
    App[URL Shortener<br/>Spring Boot 3.2, Java 17]
    DB[(SQLite<br/>WAL journaling)]
    Prom[Prometheus scrape]

    Browser -->|navigates short link| App
    Browser --> UI
    UI -->|JSON over CORS| App
    App -->|JPA / HikariCP| DB
    Prom -->|/actuator/prometheus| App
```

## Components

```mermaid
graph TD
    subgraph Filters
    RID[RequestIdFilter<br/>correlation id into MDC]
    RL[RateLimitFilter<br/>fixed window on POST /shorten]
    end

    subgraph Controllers
    UC[UrlController]
    CC[CopilotController]
    GEH[GlobalExceptionHandler<br/>the entire error contract]
    end

    subgraph Services
    SS[ShortenerService<br/>domain logic, injected Clock]
    CE[CopilotEngineService<br/>report + heuristics]
    end

    subgraph Persistence
    UR[UrlRepository<br/>atomic click increment]
    CR[ClickEventRepository<br/>max aggregate]
    U[(urls)]
    CEV[(click_events)]
    end

    RID --> RL --> UC
    UC --> SS
    CC --> CE
    SS --> UR
    SS --> CR
    UR --> U
    CR --> CEV
    U -->|1:N| CEV
    UC -.throws.-> GEH
    RL -.resolves via HandlerExceptionResolver.-> GEH
```

`RateLimitFilter` reaches `GlobalExceptionHandler` through
`HandlerExceptionResolver` rather than by throwing. A filter runs outside the
`DispatcherServlet`, so a thrown exception would bypass `@RestControllerAdvice`
entirely and produce the container's default error page — breaking the error
contract for exactly one status code. This was a real bug, caught by a test that
asserted the 429 body shape.

## Layering

| Layer | Owns | Must not know about |
| --- | --- | --- |
| Filters | Correlation, quotas | Domain concepts |
| Controllers | HTTP, validation, status codes | Persistence |
| Services | Domain logic, transactions | HTTP, `LocalDateTime.now()` |
| Repositories | Queries | HTTP, business rules |

`ShortenerService` takes a `Clock` instead of calling `now()` statically. That
one constraint is why expiry boundaries are asserted exactly rather than with
sleeps.

## Request flows

### Create

```mermaid
sequenceDiagram
    participant C as Client
    participant F as Filters
    participant Ctl as UrlController
    participant S as ShortenerService
    participant R as UrlRepository

    C->>F: POST /shorten
    F->>F: assign X-Request-Id, check quota
    F->>Ctl: forward
    Ctl->>Ctl: @Valid — scheme, length, alias charset, future expiry
    Ctl->>S: createShortUrl(...)
    loop up to 5 attempts
        S->>R: existsByShortCode(candidate)
    end
    S->>R: save
    S-->>Ctl: Url
    Ctl-->>C: 201 { short_url built from app.base-url }
```

The existence check is an early rejection, not the uniqueness guarantee — two
concurrent requests can both pass it. The unique index decides, and the loser's
`DataIntegrityViolationException` becomes a 409.

### Redirect

```mermaid
sequenceDiagram
    participant C as Client
    participant S as ShortenerService
    participant DB as Database

    C->>S: GET /{shortCode}
    S->>DB: findByShortCode (unique index)
    alt not found
        S-->>C: 404
    else expired
        S-->>C: 410
    else live
        S->>DB: insert click_event
        S->>DB: UPDATE urls SET click_count = click_count + 1
        S-->>C: 307 + Cache-Control: no-store
    end
```

The increment is SQL, not application state. Read-modify-write through the
entity lost 80% of increments at 100 concurrent requests.

## Data model

```
urls
  id            PK
  original_url  TEXT     NOT NULL
  short_code    VARCHAR(16) NOT NULL UNIQUE, INDEXED
  click_count   INTEGER  NOT NULL DEFAULT 0
  created_at    DATETIME NOT NULL
  expires_at    DATETIME NULL          -- NULL = never expires

click_events
  id            PK
  url_id        FK -> urls.id
  clicked_at    DATETIME NOT NULL
```

`click_count` is denormalised deliberately: the redirect path must not
`COUNT(*)` a growing table. `click_events` exists so time-series analytics are a
query rather than a migration plus a backfill of data that was never captured.

## Key decisions

| Decision | Rationale | Cost accepted |
| --- | --- | --- |
| 307, not 301 | A cached permanent redirect freezes the destination and silently stops click counting | An extra round trip per click |
| Random base62, not a counter | Sequential codes are enumerable — anyone can walk the keyspace and read every link | A uniqueness check and a retry budget |
| Atomic SQL increment | Read-modify-write loses concurrent updates | Entity state is stale after the call, so the method returns the destination instead |
| Pool size 1 | SQLite has one writer; a larger pool converts waiting into `SQLITE_BUSY` failures | Write throughput ceiling — removed by moving to PostgreSQL |
| WAL journaling | Readers stop blocking the writer, and every redirect is a write | A second file on disk |
| Base URL from config | `Host` is client-controlled; deriving links from it is injection | One more thing to configure per environment |
| `error_id` instead of the cause | Exception text leaked SQL and framework internals | A support flow that requires log access |
| Report in a JSON resource | Documentation edits stop being code changes, and claims can be test-verified | Loading and parsing at startup |

## Scaling path

Ordered by what binds first:

1. **PostgreSQL** — removes the single-writer ceiling. Connection string plus
   deleting the pool pin; no application code changes.
2. **Cache on redirect** — read-through on hot codes, invalidated on expiry.
3. **Async click recording** — batched writes; trades exact counts for
   throughput, a semantic change that needs sign-off.
4. **Shared rate-limit state** — Redis or gateway-enforced; the in-memory
   limiter does not survive replication.
5. **Read replicas** — analytics served off a replica, redirects off the primary.

Steps 1 and 4 are prerequisites for running more than one instance at all.

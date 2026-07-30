# URL Shortener — Frontend

React + TypeScript UI for the Spring Boot URL Shortener backend, built with Vite
and Tailwind CSS v4.

## Running

The backend must be running first (see the [backend README](../README.md)).

```bash
npm install
npm run dev
```

The app is served at **http://localhost:3000**.

### Why port 3000 is not negotiable

The backend whitelists exactly one browser origin for CORS:

```java
// UrlShortenerApplication#corsConfigurer
registry.addMapping("/**")
        .allowedOrigins("http://localhost:3000", "http://localhost:8080")
```

Vite's default port is 5173, which the browser would reject. `vite.config.ts`
therefore pins the dev server to port 3000 with `strictPort: true`, so a port
clash fails loudly instead of silently falling back to a port that cannot talk
to the API.

Actuator endpoints are served by a separate handler mapping that does **not**
inherit the rules above, so `application.properties` whitelists the frontend
origin again under `management.endpoints.web.cors.*`. Without it the header
health indicator reports "API unreachable" even though the API is fine.

Both origin lists now come from `APP_CORS_ORIGINS`, so a deployment sets one
variable rather than editing two files that must agree.

### Pointing at a different backend

The API base URL defaults to `http://localhost:8080` and can be overridden:

```bash
VITE_API_BASE=http://localhost:8081 npm run dev
```

## Scripts

| Command | Description |
| --- | --- |
| `npm run dev` | Dev server with HMR on port 3000 |
| `npm run build` | Typecheck (`tsc -b`) then production build to `dist/` |
| `npm run preview` | Serve the production build locally |
| `npm run lint` | Run oxlint |

## Structure

```
src/
  api/
    types.ts         Mirrors the backend DTOs (all JSON is snake_case)
    client.ts        fetch wrapper; throws ApiError carrying the backend `detail`
  components/
    Shortener.tsx    POST /shorten — custom alias and expiry
    Analytics.tsx    GET /analytics/{shortCode}
    CopilotPanel.tsx POST /copilot/analyze — tabbed report
    ui.tsx           Shared presentational primitives
  lib/format.ts      LocalDateTime parsing and severity/badge mapping
  App.tsx            Top-level view switching and API health indicator
```

## Error handling

`api/client.ts` throws `ApiError` for every non-2xx. Three things come off the
backend's error envelope:

- `detail` — always safe to display.
- `error_id` — 5xx only. `ApiError.displayMessage` appends it, so a user
  reporting a failure quotes something the server logs can be searched by.
- `field_errors` — validation failures. Flattened into the message so the user
  sees which field is wrong instead of a bare "Validation failed".

## Notes for future work

- **No automated tests.** The app is typechecked (`tsc -b`) and linted, but has
  no test suite — see `docs/LIMITATIONS.md`. `api/types.ts` mirrors the backend
  DTOs by hand, so a backend contract change is only caught at compile time if
  someone updates the types too. Generating them from `/v3/api-docs` would
  remove that failure mode.
- **Trust `expired` from the server, never compare `expires_at` locally.** The
  backend sends a zone-less `LocalDateTime`; a client-side comparison is wrong
  for any user outside the server's timezone.

- **Short links are rendered as plain `<a href>`, never fetched.**
  `GET /{shortCode}` returns a 307 to an arbitrary external origin; following
  that from `fetch()` fails CORS. Let the browser navigate instead.
- **`LocalDateTime` has no timezone and up to 9 fractional digits.**
  `Date.parse` only handles milliseconds reliably, so `lib/format.ts` trims to
  3 digits and treats the value as local wall-clock time.
- **Do not serve this app from Spring Boot's static resources as-is.**
  `GET /{shortCode}` is a root-level catch-all and will swallow frontend routes.
  Mount the UI under a path prefix if you need single-origin deployment.
- **Vite is pinned to 7.x.** Vite 8 uses rolldown, whose native binary is
  blocked by Application Control policy on some Windows machines; it then falls
  back to a WASM build that cannot resolve entry modules on Windows paths.

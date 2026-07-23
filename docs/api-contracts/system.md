# System API

Operational and discovery endpoints. All are **public** (no authentication).

See [README.md](README.md) for global conventions.

## GET /health

Liveness/readiness probe. Use it for uptime checks, load-balancer health checks, and to
confirm the server is reachable before attempting auth.

**Request** — no body, no auth:

```
GET /health
```

**`200 OK`** — a JSON object (`Map<String, String>`):

```json
{ "status": "ok" }
```

Returns `200` whenever the HTTP server is up. (It does not currently probe the database.)

## Discovery & tooling

These help you explore and test the API; they are not part of a normal client flow.

| Endpoint | Returns | Notes |
|----------|---------|-------|
| `GET /api.json` | OpenAPI 3 spec (JSON) | Machine-readable source of truth for every documented route/schema. Import into Postman, codegen, etc. |
| `GET /swagger` | Swagger UI (HTML) | Interactive explorer. Click **Authorize** and paste a bearer token to try protected endpoints. |
| `GET /metrics-micrometer` | Prometheus metrics (**plain text**) | Ops/monitoring scrape endpoint. **Not** JSON and **not** listed in `/api.json`. |

> `/metrics-micrometer` returns Prometheus text-exposition format, not JSON — don't parse
> it as JSON. It is intended for a metrics scraper, not the app front-end.

## See also

- [auth.md](auth.md) — authentication endpoints & the login → refresh → logout sequence.

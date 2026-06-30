# Tech Architecture

## Platforms
- **Web** (primary) and **tablet** (chairside). Single responsive web app rather
  than a separate native build; consider PWA install for the tablet so it
  behaves app-like.

## Stack (Firebase)
- **Firebase App Hosting** — hosting/deploy for the web app (git-based deploys,
  SSR-capable frameworks).
- **Firebase SQL Connect** (formerly Data Connect) — the backend. It is a
  managed **PostgreSQL** database on **Cloud SQL**, where you declare a
  **GraphQL schema** and it generates: the Postgres tables, secure server-side
  queries/mutations (stored on the server like Cloud Functions, not sent from
  the client), and **type-safe web SDKs**.
- **Firebase Authentication** — login/identity. Roles enforced via **custom
  claims** + SQL Connect **`@auth` directives** on each query/mutation.

### Why this fits
Our data model is relational, so Postgres/SQL Connect is a natural match (the
ERD maps directly to tables). Auth + `@auth` gives the role enforcement that the
new two-role requirement needs (see `access-control-and-roles.md`).

### SQL Connect constraints to design around
- **Field names cannot contain underscores** in the GraphQL schema — SQL Connect
  uses `_` for generated fields. Use **camelCase** GraphQL fields (e.g.
  `dateOfBirth`), which SQL Connect maps to snake_case Postgres columns
  (`date_of_birth`). The snake_case names in `data-model.md` are the conceptual
  columns; author the schema in camelCase.
- IDs are typically `UUID` with `@default(expr: "uuidV4()")`.
- Authorization is per-operation via `@auth(level: ...)`; design queries/mutations
  with the role model in mind from the start.
- Schema changes are migrated via `firebase dataconnect:sql:diff`.

## Data privacy (Republic Act 10173 — Data Privacy Act)
This is sensitive personal/health data on a cloud backend, so beyond
"login + backups":
- Encryption at rest (Cloud SQL) and in transit (default with Firebase).
- **Access logging / audit trail** — required now that multiple users (dentist +
  staff) can sign in. See `AUDIT_LOG` in `data-model.md`.
- A data **retention policy** and a documented basis/consent for processing
  (tie to the data-privacy consent in `patient-record-and-consent.md`).
- Restrict who can run privileged/admin operations (IAM, not the app's staff
  role).

> NOTE: Data residency and DPA compliance specifics should be reviewed with a
> privacy/compliance advisor; this captures the engineering implications only.

## Decided / open items
- **Tablet delivery** (confirmed): installable **PWA** of the responsive web app.
- **Backups** (confirmed): daily Cloud SQL backups + point-in-time recovery,
  ~30-day retention, region `asia-southeast1` (Singapore; no GCP region in PH).
- **Staff access** enforced via SQL Connect `@auth` query design (no separate
  read-model unless needed).
- Open (external): privacy/compliance sign-off on data residency + retention.

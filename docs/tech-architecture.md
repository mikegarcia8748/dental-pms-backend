# Tech Architecture

## Platforms
- **Web** (primary) and **tablet** (chairside). Single responsive web app rather
  than a separate native build; consider PWA install for the tablet so it
  behaves app-like.

## Stack
- **Language / build**: Kotlin (JVM 21), built with **Amper**.
- **Backend**: **Ktor** (Netty engine), packaged as a container image and deployed
  to **Google Cloud Run**. In development the same image runs on Docker Desktop,
  and a fast edit/run loop is available via `./kotlin run`.
- **Database**: **Neon** serverless PostgreSQL. Our data model is relational, so
  Postgres maps directly to the ERD in `data-model.md`.
- **Data access**: **Exposed** (typed SQL DSL) over a **HikariCP** connection
  pool. Schema is owned by SQL and applied with **Flyway** migrations
  (`resources/db/migration`), not generated from a schema language.
- **Authentication**: **self-hosted email + password**. The backend issues a
  short-lived **JWT access token** and a **rotating, revocable refresh token**
  (only the token's hash is stored). Passwords are hashed with **bcrypt**. See
  `access-control-and-roles.md`.
- **Authorization**: **role-based access control enforced server-side in Ktor** —
  the user's role travels as a JWT claim and each protected route is guarded by a
  role check. Not enforced in the UI alone.
- **DI**: Koin. **Observability**: call logging + Micrometer/Prometheus.

### Why this fits
A relational clinical/billing model is a natural fit for Postgres. Cloud Run gives
us a stateless, containerized backend that scales to zero and deploys from the
same image we run locally, and Neon provides a managed Postgres with a generous
serverless model. Self-hosted JWT auth gives us full control over the role
enforcement the two-role requirement needs (see `access-control-and-roles.md`).

### Schema conventions to design around
- Columns are **snake_case** in Postgres, authored directly in the Flyway
  migration SQL (e.g. `date_of_birth`). Exposed table objects mirror those
  columns; the Kotlin domain models expose camelCase properties. The snake_case
  names in `data-model.md` are the actual column names.
- IDs are `UUID` (application-generated).
- Authorization is applied per route in the backend; design routes and use cases
  with the role model in mind from the start.
- Schema changes ship as new **Flyway** migration files (`V2__…`, `V3__…`);
  migrations are versioned and never edited once applied.

## Data privacy (Republic Act 10173 — Data Privacy Act)
This is sensitive personal/health data on a cloud backend, so beyond
"login + backups":
- Encryption at rest (managed by Neon) and in transit (TLS / `sslmode=require`).
- **Access logging / audit trail** — required now that multiple users (SysAdmin +
  Dentist) can sign in. See `AUDIT_LOG` in `data-model.md`.
- A data **retention policy** and a documented basis/consent for processing
  (tie to the data-privacy consent in `patient-record-and-consent.md`).
- Restrict who can run privileged/admin operations (the **SysAdmin** role plus
  cloud IAM on the deployment itself), not the day-to-day clinical login.

> NOTE: Data residency and DPA compliance specifics should be reviewed with a
> privacy/compliance advisor; this captures the engineering implications only.

## Decided / open items
- **Tablet delivery** (confirmed): installable **PWA** of the responsive web app.
- **Backups** (confirmed): rely on Neon's managed backups / point-in-time
  recovery; keep ~30-day retention. Nearest region is Singapore
  (`ap-southeast-1`); there is no cloud region in the Philippines.
- **Authorization**: enforced server-side per route via the role model (no
  separate read-model unless one is later needed).
- Open (external): privacy/compliance sign-off on data residency + retention.

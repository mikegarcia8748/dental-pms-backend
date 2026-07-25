# Environments

The backend runs in three environments, selected at runtime by the `APP_ENV` variable. Each has
its **own Neon Postgres database** and its **own value for the single `DATABASE_URL` variable** —
there is no code that hardcodes or switches between connection strings.

| Env       | `APP_ENV` | Runs where                                   | Database (Neon branch) | `DATABASE_URL` comes from            |
|-----------|-----------|----------------------------------------------|------------------------|--------------------------------------|
| **local** | `local`   | Your machine (`./kotlin run` / `kotlin.bat`) | `local` branch         | `.env` file (copy from `.env.example`) |
| **dev**   | `dev`     | Cloud Run (for front-end dev & testing)      | `dev` branch           | `deploy/cloudrun/service.dev.yaml` + Secret Manager |
| **prod**  | `prod`    | Cloud Run (live)                             | `prod` (main) branch   | `deploy/cloudrun/service.prod.yaml` + Secret Manager |

## Configuration model

All configuration is environment-variable driven through the `Env` object in
[`src/Config.kt`](../src/Config.kt) — real environment variables win, and a `.env` file in the
working directory fills in the rest for local runs. There is **no `application.conf`**.

- **One `DATABASE_URL` per environment.** The app always reads a single `DATABASE_URL`
  ([`DatabaseConfig`](../src/config/DatabaseConfig.kt)); each environment supplies its own value.
  The production connection string never lives in the repo — only in the prod Cloud Run service.
- **`APP_ENV`** selects the profile (`AppConfig` in `src/Config.kt`): `prod`/`production` → `PROD`
  (turns on production-only guards), `local` → `LOCAL`, anything else/unset → `DEV`.
- **`CORS_ALLOWED_HOSTS`** is a fail-fast guard in production: with `APP_ENV=prod` the app refuses
  to start unless it is set. In `local`/`dev` an unset value falls back to a localhost allowlist.

### Database connection variables

| Variable             | Required | Notes                                                      |
|----------------------|----------|------------------------------------------------------------|
| `DATABASE_URL`       | yes      | JDBC URL. Use the Neon **direct (non-pooled)** endpoint (no `-pooler` in the host) so Flyway's migration lock has a real session. `sslmode=require`. |
| `DATABASE_USER`      | yes      | Usually `neondb_owner`.                                     |
| `DATABASE_PASSWORD`  | yes      | Local: `.env`. dev/prod: Secret Manager (never in a manifest). |
| `DATABASE_POOL_SIZE` | no (5)   | Keep small per instance — Neon caps connections; Cloud Run scales horizontally. Suggested: local 5, dev 5, prod 10. |

---

## Neon setup (one time)

Create a branch per environment so they never share data. In the Neon console (or CLI), from your
project create branches **`local`**, **`dev`**, and **`prod`** (or use the default/main branch as
`prod`). For each branch, grab the **direct** connection endpoint — the host **without** `-pooler`.

Example JDBC URL (per branch):

```
jdbc:postgresql://ep-xxxx-xxxxxxxx.ap-southeast-1.aws.neon.tech/dental_pms?sslmode=require
```

> Flyway runs migrations on startup against `DATABASE_URL`. It takes a Postgres advisory lock, which
> requires a real session — that is why we use the **direct** endpoint, not the pooled one. When
> several Cloud Run instances start at once, the lock serializes their `migrate()` calls safely.

---

## Local environment

1. Copy the template and fill it in:
   ```bash
   cp .env.example .env
   ```
2. In `.env` set:
   - `APP_ENV=local`
   - `DATABASE_URL` → your Neon **local** branch direct endpoint, `DATABASE_USER`, `DATABASE_PASSWORD`
   - `JWT_SECRET` → 32+ chars (`openssl rand -base64 48`)
   - `CORS_ALLOWED_HOSTS` → your front-end dev origin(s), e.g. `localhost:5173`
3. Run the backend:
   ```powershell
   .\kotlin.bat run       # Windows
   ./kotlin run           # macOS/Linux
   ```
4. Verify: `GET http://localhost:8080/health` → `{"status":"ok"}`, and check the log for Flyway
   migrating the Neon local branch.

> Offline fallback: `compose.yaml` still has a `local-db` profile (local Postgres 17) if you want to
> develop without Neon — `docker compose --profile local-db up`.

---

## Cloud Run environments (dev & prod)

Deployment artifacts live in [`deploy/`](../deploy):

- `deploy/cloudrun/service.dev.yaml`, `deploy/cloudrun/service.prod.yaml` — declarative Knative
  service manifests (env vars, Secret Manager refs, `/health` probes, autoscaling).
- `deploy/deploy.ps1` — builds the image, pushes it to Artifact Registry, substitutes `__IMAGE__`
  in the chosen manifest, and applies it with `gcloud run services replace`.

### One-time GCP setup

Set your identifiers (example region: `asia-southeast1`, Singapore — matches the Neon region):

```bash
PROJECT_ID=your-gcp-project
REGION=asia-southeast1
REPO=dental-pms

gcloud config set project "$PROJECT_ID"
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com

# Artifact Registry (Docker) repo
gcloud artifacts repositories create "$REPO" \
  --repository-format=docker --location="$REGION" \
  --description="dental-pms container images"

# Let local docker push to Artifact Registry
gcloud auth configure-docker "${REGION}-docker.pkg.dev"
```

Create the secrets each service references (repeat with `prod` for the prod service):

```bash
# DB password + JWT secret (per environment)
printf '%s' 'YOUR_NEON_DEV_PASSWORD' | gcloud secrets create dental-pms-dev-db-password --data-file=-
openssl rand -base64 48 | tr -d '\n' | gcloud secrets create dental-pms-dev-jwt-secret --data-file=-
```

Grant the Cloud Run runtime service account access to read secrets (default compute SA shown; use
your dedicated runtime SA if you have one):

```bash
PROJECT_NUMBER=$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')
RUNTIME_SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
for s in dental-pms-dev-db-password dental-pms-dev-jwt-secret; do
  gcloud secrets add-iam-policy-binding "$s" \
    --member="serviceAccount:${RUNTIME_SA}" --role="roles/secretmanager.secretAccessor"
done
```

### Fill in the manifests

Edit `deploy/cloudrun/service.dev.yaml` (and `service.prod.yaml`) and replace the placeholders:

- `REPLACE_NEON_DEV_DIRECT_HOST` / `REPLACE_NEON_PROD_DIRECT_HOST` — the Neon **direct** endpoint host.
- `REPLACE_FE_DEV_ORIGIN` / `REPLACE_FE_PROD_ORIGIN` — the front-end origin as `host[:port]` (no scheme).

(The deploy script refuses to deploy while any `REPLACE_` placeholder remains.)

### Deploy

```powershell
# Provide identifiers via params or env vars
$env:GCP_PROJECT_ID = 'your-gcp-project'
$env:GCP_REGION     = 'asia-southeast1'
$env:GCP_AR_REPO    = 'dental-pms'

.\deploy\deploy.ps1 -Environment dev
# ... later, when ready:
.\deploy\deploy.ps1 -Environment prod
```

The script prints the service URL; verify `GET <url>/health` → `{"status":"ok"}` and confirm the log
shows Flyway migrating that environment's Neon branch. For the dev service, confirm the front-end can
call it (CORS headers present for `CORS_ALLOWED_HOSTS`).

---

## First-run bootstrap accounts

The app seeds initial accounts **only while `app_user` is empty** (idempotent), from
`BOOTSTRAP_SYSADMIN_*` / `BOOTSTRAP_DENTIST_*`. For a brand-new environment:

1. Create the bootstrap values as secrets and uncomment the `BOOTSTRAP_*` blocks in the manifest for
   the **first** deploy.
2. Deploy, log in, and change the passwords.
3. Remove the `BOOTSTRAP_*` blocks (and delete the secrets) and redeploy.

For local, just set the `BOOTSTRAP_*` values in `.env` for the first run.

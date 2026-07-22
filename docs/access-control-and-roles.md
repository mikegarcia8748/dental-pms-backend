# Access Control and Roles

> This supersedes the earlier "single persona, no RBAC" decision. The system has
> **two roles**, enforced server-side with JWT authentication and role-based
> authorization in the Ktor backend (see `tech-architecture.md`).

## Roles
### SysAdmin
Owns **data and system configuration** — user accounts and reference/configuration
data (for example the medical-history question set, discount/VAT settings, and
condition codes). Its detailed operational scope is planned in a later phase.
SysAdmin is **not a clinical actor** — it does not run the visit workflow.

### Dentist (full access)
Full access to **all clinical and billing operations**: create/edit patients,
medical history, diagnoses, dental chart, procedures, treatment plans, billing,
discounts, payments, and viewing all history. The Dentist is the only role that
performs the clinical workflow (see `visit-flow.md`).

> The earlier read-only **Staff / Assistant** role is **not in current scope**.
> The two active personas are SysAdmin and Dentist. A limited staff/receptionist
> role (read-only patient list / minor details) can be revisited later; the RBAC
> design below is built so roles can be added without rework.

## Enforcement
- Identity via **self-hosted email + password**. On login the backend issues a
  short-lived **JWT access token** and a **rotating, revocable refresh token**
  (only the refresh token's hash is stored; refreshing rotates it, logout revokes
  it). Passwords are hashed with **bcrypt**.
- The user's **role travels as a claim in the JWT**; the `role` column on the
  `app_user` table is the source of truth.
- Authorization is enforced **server-side in Ktor**: each protected route is
  guarded by a role check (`authorize(Role.…)`), returning **401** when
  unauthenticated and **403** for the wrong role. Do **not** rely on hiding
  actions in the UI alone.
- Accounts can be **deactivated** (`active = false`): a deactivated user can
  neither log in nor refresh.

## First-run bootstrap
When the user table is empty, the app seeds the initial **SysAdmin** and
**Dentist** accounts from environment variables (each role is seeded only if its
email and password are configured). Bootstrap is idempotent — a restart with
existing accounts does nothing. Change the seed passwords immediately after the
first login.

## Accountability
Because more than one person can sign in, every create/edit/delete records the
acting user. See `AUDIT_LOG` in `data-model.md`. No shared logins — each person
authenticates as themselves.

## Future
- Detailed **SysAdmin** operational endpoints (user management, configuration data).
- Optional limited **Staff / receptionist** role if the clinic needs a read-only
  patient list / minor-details view.
- Multi-dentist support (multiple full-access practitioners, per-dentist
  attribution of procedures/bills). The `app_user` + role model and
  `performed_by` / `recorded_by` fields are designed so these can be added
  without a rework.

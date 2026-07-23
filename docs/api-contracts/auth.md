# Auth API

Authentication and session management. Base path: **`/auth`**.

All endpoints exchange JSON. Only `GET /auth/me` requires a bearer token; the other
three are public (each is authorized by the credentials or refresh token in its body).

See [README.md](README.md) for global conventions (base URL, error envelope, token
lifetimes, roles).

## The right process

```mermaid
sequenceDiagram
    participant FE as Front-end
    participant API as Backend

    FE->>API: POST /auth/login (email, password)
    API-->>FE: 200 LoginResponse (user + tokens)
    Note over FE: store accessToken + refreshToken

    FE->>API: GET /auth/me (Bearer accessToken)
    API-->>FE: 200 UserResponse

    Note over FE,API: accessToken expired, 401
    FE->>API: POST /auth/refresh (refreshToken)
    API-->>FE: 200 TokenResponse (new pair; old refresh revoked)
    Note over FE: replace BOTH stored tokens

    FE->>API: POST /auth/logout (refreshToken)
    API-->>FE: 204 No Content
```

1. **Log in** with email + password → store the returned `accessToken` and `refreshToken`.
2. **Use** `accessToken` as `Authorization: Bearer <accessToken>` on protected routes.
3. **Refresh** with `refreshToken` when the access token nears/reaches expiry; store the
   **new** pair (the old refresh token is immediately revoked).
4. **Log out** with `refreshToken` to end the session.

> **No signup endpoint.** Accounts are provisioned server-side (see
> [Bootstrap](#bootstrap--how-accounts-are-created) below). There is no
> `POST /auth/register`.

---

## POST /auth/login

Exchange email + password for a user profile and a token pair. Public.

**Request** — `LoginRequest`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `email` | string | yes | Case-insensitive; trimmed + lowercased server-side. |
| `password` | string | yes | Sent in the body over TLS; verified against a bcrypt hash. |

```json
POST /auth/login
Content-Type: application/json

{ "email": "dentist@example.com", "password": "s3cret-passphrase" }
```

**`200 OK`** — `LoginResponse`

| Field | Type | Notes |
|-------|------|-------|
| `user` | `UserResponse` | The authenticated user (see [Data types](#data-types)). |
| `tokens` | `TokenResponse` | Access + refresh token pair. |

```json
{
  "user": {
    "id": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
    "email": "dentist@example.com",
    "displayName": "Dr. Reyes",
    "role": "DENTIST"
  },
  "tokens": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ...<jwt>",
    "refreshToken": "gH7s...<opaque-random>",
    "expiresIn": 900
  }
}
```

> **`tokenType` is omitted from the JSON.** `TokenResponse` has a `tokenType` field that
> always equals `"Bearer"`, but the server does not serialize default values, so the
> field is **absent from the response**. Treat the token type as `"Bearer"`
> unconditionally — do not read `tokens.tokenType`.

**Errors**

| Status | `error` | When |
|--------|---------|------|
| `401 Unauthorized` | `invalid_credentials` | Unknown email **or** wrong password — deliberately indistinguishable. |
| `403 Forbidden` | `inactive_account` | Credentials were correct but the account is deactivated. |

---

## POST /auth/refresh

Rotate a valid refresh token for a brand-new token pair. Public (the refresh token is
the credential).

**Request** — `RefreshRequest`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `refreshToken` | string | yes | The opaque refresh token from the last login/refresh. |

```json
POST /auth/refresh
Content-Type: application/json

{ "refreshToken": "gH7s...<opaque-random>" }
```

**`200 OK`** — `TokenResponse` (a **new** pair)

```json
{
  "accessToken": "eyJ...<new-jwt>",
  "refreshToken": "kP2m...<new-opaque-random>",
  "expiresIn": 900
}
```

> **Rotation — single use.** The refresh token you send is **revoked** the moment it is
> accepted, and a **new** one is returned. Always persist the new `refreshToken`; the old
> one will never work again. Don't refresh the same token twice — serialize concurrent
> refreshes (single-flight).

**Errors**

| Status | `error` | When |
|--------|---------|------|
| `401 Unauthorized` | `invalid_refresh_token` | Token is unknown, malformed, expired, or already used/revoked. |
| `403 Forbidden` | `inactive_account` | The owning account has been deactivated. |

---

## POST /auth/logout

Revoke a refresh token. Public and **idempotent**.

**Request** — `LogoutRequest`

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `refreshToken` | string | yes | The refresh token to revoke. |

```json
POST /auth/logout
Content-Type: application/json

{ "refreshToken": "kP2m...<opaque-random>" }
```

**`204 No Content`** — no response body. Returned whether or not the token existed, so
logout is safe to call blindly.

> Logout revokes the **refresh** token only. The access token is a stateless JWT and is
> **not** tracked server-side — it stays valid until its own `exp` (≤ 15 min). For an
> immediate hard sign-out, also discard the access token on the client.

---

## GET /auth/me

Return the profile of the authenticated user. **Requires a bearer token** (any role).

**Request** — no body:

```
GET /auth/me
Authorization: Bearer <accessToken>
```

**`200 OK`** — `UserResponse`

```json
{
  "id": "6f9619ff-8b86-d011-b42d-00cf4fc964ff",
  "email": "dentist@example.com",
  "displayName": "Dr. Reyes",
  "role": "DENTIST"
}
```

**Errors**

| Status | `error` | `message` | When |
|--------|---------|-----------|------|
| `401 Unauthorized` | `unauthorized` | `Missing or invalid token` | No token, or a malformed / expired / wrong-issuer token, or an unknown `role` claim. |

---

## Data types

All auth DTOs (defined in `src/auth/AuthDtos.kt`). Every field is **required and
non-null** on the wire unless noted.

### `LoginRequest`

| Field | Type |
|-------|------|
| `email` | string |
| `password` | string |

### `RefreshRequest` / `LogoutRequest`

| Field | Type |
|-------|------|
| `refreshToken` | string |

### `TokenResponse`

| Field | Type | Notes |
|-------|------|-------|
| `accessToken` | string | JWT (HMAC-SHA256). Send as `Authorization: Bearer`. |
| `refreshToken` | string | Opaque random string; single-use. |
| `expiresIn` | number | **Access-token** lifetime in **seconds** (default `900`). |
| `tokenType` | string | Always `"Bearer"` — **omitted from the JSON** (see the login note). |

### `UserResponse`

| Field | Type | Notes |
|-------|------|-------|
| `id` | string | UUID. |
| `email` | string | |
| `displayName` | string | |
| `role` | string | `SYSADMIN` or `DENTIST`. |

### `LoginResponse`

| Field | Type |
|-------|------|
| `user` | `UserResponse` |
| `tokens` | `TokenResponse` |

### `ErrorResponse`

| Field | Type | Notes |
|-------|------|-------|
| `error` | string | Stable machine code — branch on this. |
| `message` | string | Human-readable; may change. |

## The access token (JWT)

The access token is a standard JWT with the claims below. **The front-end should treat
it as opaque** and use `GET /auth/me` for user info rather than decoding it; the claims
are listed only for troubleshooting.

| Claim | Meaning |
|-------|---------|
| `sub` | User id (UUID string). |
| `role` | `SYSADMIN` or `DENTIST`. |
| `email` | User email. |
| `name` | Display name. |
| `iss` / `aud` | Issuer / audience (default `dental-pms` / `dental-pms-web`). |
| `iat` / `exp` | Issued-at / expiry (epoch seconds). |

## Bootstrap — how accounts are created

There is no registration API. On first boot **with an empty user table**, the server
seeds the initial accounts from environment variables (a server-operator concern, not a
client call):

| Role | Env vars | Notes |
|------|----------|-------|
| `SYSADMIN` | `BOOTSTRAP_SYSADMIN_EMAIL`, `BOOTSTRAP_SYSADMIN_PASSWORD`, `BOOTSTRAP_SYSADMIN_NAME` | Seeded only if email **and** password are set; `NAME` optional (defaults to the role name). |
| `DENTIST` | `BOOTSTRAP_DENTIST_EMAIL`, `BOOTSTRAP_DENTIST_PASSWORD`, `BOOTSTRAP_DENTIST_NAME` | Same rules. |

Bootstrap is idempotent (a restart with existing users does nothing). Seeded users then
sign in through `POST /auth/login` like anyone else. See
[access-control-and-roles.md](../access-control-and-roles.md).

## See also

- [system.md](system.md) — health & discovery endpoints.
- [access-control-and-roles.md](../access-control-and-roles.md) — role model & enforcement.

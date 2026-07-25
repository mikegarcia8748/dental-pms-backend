# Patients & Intake

Endpoints for registering patients with their full PDA-form intake (demographics, structured
allergies, a configurable medical/dental questionnaire, and recorded consents), plus search and
edit. Mirrors the official PDA "Patient Information Record" — see
[`../reference/PDA-Dental-Chart.pdf`](../reference/PDA-Dental-Chart.pdf).

**All endpoints require a Dentist bearer token** (`Authorization: Bearer <accessToken>` from
[`auth.md`](auth.md)). A missing/invalid token → `401`; a non-Dentist (e.g. SysAdmin) token → `403`.
Wire conventions are the global ones in [`README.md`](README.md): camelCase JSON, UUID **strings**,
temporals as **ISO-8601 strings** (`dateOfBirth` = `"1990-05-01"`, timestamps = ISO instants),
enums as their name strings, and the `{ "error", "message" }` envelope on non-2xx.

## Call sequence (registering a patient)

1. **`GET /intake-questions?section=MEDICAL`** and **`?section=DENTAL`** — fetch the question set to
   render the form. Each question has an `id`, `code`, `prompt`, and `answerType`
   (`BOOLEAN` | `TEXT` | `DATE` | `CHOICE`).
2. **`GET /consent-texts`** — fetch the consent bodies + `version`s to display (at least the
   `TREATMENT` and `DATA_PRIVACY` texts).
3. **`POST /patients`** — submit the profile plus the collected `allergies`, `answers` (keyed by the
   question `id` from step 1), and `consents` (referencing the `type`+`version` from step 2), in one
   atomic call.

## Endpoints

| Method & path | Purpose | Success |
|---|---|---|
| `POST /patients` | Register (profile + nested allergies/answers/consents) | `201` `PatientDetails` |
| `GET /patients?q=&page=&limit=&includeInactive=` | Search by name, paged | `200` `PatientPage` |
| `GET /patients/{id}` | Full record | `200` `PatientDetails` |
| `PUT /patients/{id}` | Replace the profile | `200` `Patient` |
| `POST /patients/{id}/deactivate` | Soft-delete | `204` |
| `POST /patients/{id}/allergies` | Add an allergy | `201` `Allergy` |
| `PUT /patients/{id}/allergies/{allergyId}` | Edit an allergy | `200` `Allergy` |
| `POST /patients/{id}/allergies/{allergyId}/deactivate` | Soft-delete an allergy | `204` |
| `PUT /patients/{id}/intake-answers` | Set/replace answers | `200` `IntakeAnswer[]` |
| `POST /patients/{id}/consents` | Record a consent acknowledgment | `201` `Consent` |
| `GET /intake-questions?section=` | Active question set | `200` `IntakeQuestion[]` |
| `GET /consent-texts?type=` | Active consent texts | `200` `ConsentText[]` |

### `POST /patients`

Request:

```json
{
  "profile": {
    "lastName": "Dela Cruz",
    "firstName": "Juan",
    "sex": "MALE",
    "dateOfBirth": "1990-05-01",
    "mobileNumber": "09170000000",
    "isSenior": false,
    "isPwd": false,
    "referralSource": "Dr. Santos",
    "isLegacy": false
  },
  "allergies": [
    { "substance": "Penicillin", "severity": "SEVERE", "note": null }
  ],
  "answers": [
    { "questionId": "5f3e...uuid", "answerBoolean": true },
    { "questionId": "9a1c...uuid", "answerText": "O+" }
  ],
  "consents": [
    { "type": "DATA_PRIVACY", "textVersion": "RA10173-v1", "acknowledgedByRole": "PATIENT" },
    { "type": "TREATMENT", "textVersion": "PDA-2010", "acknowledgedByRole": "PATIENT" }
  ]
}
```

An answer sets exactly the one value field matching its question's `answerType`
(`answerBoolean` | `answerText` | `answerDate`). Response `201` is the `PatientDetails` shape
(`{ "patient": {...}, "allergies": [...], "answers": [...], "consents": [...] }`).

**Business rejections → `400`** (branch on the stable `error` code):

| `error` | When |
|---|---|
| `minor_requires_guardian` | `dateOfBirth` makes the patient < 18 but `guardianName`/`guardianContact` are missing |
| `missing_data_privacy_consent` | a non-legacy registration with no `DATA_PRIVACY` consent |
| `unknown_question` | an answer references an unknown/inactive question |
| `answer_type_mismatch` | an answer value doesn't match its question's type |
| `unknown_consent_text` | a consent references a `(type, version)` with no consent text |
| `future_registration_date` | a legacy `registeredAt` is in the future |
| `invalid_request` | malformed body / bad shape (blank name, unparseable date/enum, …) |

### Legacy (paper-only) patients

Set `profile.isLegacy = true` to backfill an old patient whose record only exists on paper:
`dateOfBirth`, `mobileNumber`, and the data-privacy consent become optional, a **past**
`profile.registeredAt` (ISO instant) is honored as the original join date, and
`profile.legacySummary` holds the free-text narrative of the old chart. The system's own
`createdAt` still reflects the real insert time (audit is never backdated).

### `GET /patients`

Returns `{ "patients": PatientSummary[], "total": <long>, "page": <int>, "limit": <int> }`.
`limit` is clamped to 1..100; `includeInactive` defaults to `false`.

## Notes

- Every create/edit/deactivate is attributed to the acting Dentist and written to the audit log.
- Nothing is hard-deleted — `deactivate` flips `active` to false (idempotent).
- The question set and consent texts are **seeded** (from the PDA form) and read-only via the API in
  this slice; a SysAdmin editor comes later. The tables are versioned so historical answers/consents
  stay tied to the exact version presented.
- The machine-readable schemas for every DTO here are in the live spec at `GET /api.json`
  (Swagger UI at `GET /swagger`); if this doc disagrees with the spec, the spec wins.

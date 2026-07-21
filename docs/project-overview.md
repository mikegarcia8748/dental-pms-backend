# Project Overview — Dental Clinic Management System

## Goal
Provide a digital way for a dental clinic to manage patients: store patient
records and diagnoses, maintain a dental chart, record procedures, and generate
billing and payments. Replaces the clinic's paper/manual process.

## Platforms and stack
- **Web + tablet** (chairside), delivered as one responsive web app.
- **Firebase**: App Hosting (deploy), SQL Connect / managed PostgreSQL on
  Cloud SQL (backend), Authentication (login + roles). See
  `tech-architecture.md`.

## Roles (updated — no longer single-persona)
The earlier "single persona" assumption is **superseded**. There are two roles:
- **Dentist** — full access to everything.
- **Staff / Assistant** — read-only view of the patient list and minor details.

See `access-control-and-roles.md`.

## In scope (v1)
- Patient profiles with structured intake (medical history, allergies, consent)
  and search — see `patient-record-and-consent.md`
- Visit/encounter records (the connective record per sitting)
- Treatment plans for multi-visit cases (e.g. wisdom tooth removal)
- Diagnosis notes
- PDA-format dental chart with current condition + planned-treatment overlay —
  see `dental-chart-pda-spec.md`
- Procedure entry
- Billing: editable line items, mandated PH discounts, VAT, patient balances and
  installments — see `billing-and-payments.md`
- Payment + ledger
- Patient history view
- Two-role access control + audit trail

## Out of scope (v1, revisit later)
- Receptionist "call the patient" step / waiting queue
- Appointment scheduling
- Multiple full-access dentists (RBAC is designed to allow it later)
- Reporting dashboards
- Insurance claim processing

## Key concept: the Visit
Steps 2–7 of the manual flow happen within a single sitting, modeled as a
**Visit** (Encounter). Visits can belong to a **Treatment Plan** when a case
spans multiple appointments. See `clinical-workflow.md` and `data-model.md`.

## Non-negotiable from day one
Sensitive health data under the Data Privacy Act (RA 10173): protected per-user
login, audit logging, encryption, automated backups, and a processing basis /
consent. See `tech-architecture.md`.

## Doc index
- `tech-architecture.md` — Firebase stack + privacy
- `access-control-and-roles.md` — roles and enforcement
- `clinical-workflow.md` — the visit flow
- `data-model.md` — entities, fields, relationships
- `dental-chart-pda-spec.md` — PDA dental chart
- `patient-record-and-consent.md` — intake, medical history, consent
- `billing-and-payments.md` — bills, discounts, VAT, installments
- `feature-roadmap.md` — phased build plan
- `open-questions-and-decisions.md` — decided vs. open

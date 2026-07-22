# Decisions and Open Questions

## Decided
- **Platforms**: web + tablet (chairside), one responsive web app; tablet
  delivered as an installable **PWA**.
- **Stack**: Kotlin/Ktor backend on Google Cloud Run, Neon PostgreSQL
  (Exposed + HikariCP + Flyway), self-hosted email+password JWT auth, Koin DI.
- **Two roles** (supersedes single-persona): **SysAdmin** (data & configuration;
  detailed scope planned later) and **Dentist** (full clinical/billing access).
  RBAC enforced server-side + audit required in v1. (A read-only staff/receptionist
  role is out of current scope and can be added later.)
- **Visit/encounter** is explicit; **treatment plans** group multi-visit cases.
- **Bill scope**: billed **per visit**, with an optional link to the treatment
  plan for grouping; a plan-level statement view comes later.
- **Payments / installments**: v1 supports partial payments + carried ledger
  balance (covers installments functionally). A formal **installment schedule**
  (due dates/amounts/reminders) is Phase 2.
- **Refunds / write-offs**: Phase 2, as ledger adjustment entries. v1 has `void`
  for bills.
- **Mandated discounts**: senior (RA 9994) + PWD (RA 10754) = 20% + VAT
  exemption; configurable discount engine; current "no double discount."
  VAT (12%) configurable.
- **Dental chart**: official PDA format, FDI notation; current condition and
  planned treatment stored separately (a tooth can have both); three-way
  age-based dentition default + manual toggle; per-tooth notes. **Condition
  codes confirmed: H / D / F / X / C / I / P.**
- **A tooth supports multiple planned treatments** over time.
- **Intake at launch**: **full digital** — structured allergies, configurable
  medical-history questionnaire, and consent, all captured in the system.
- **Consent**: recorded acknowledgment (who/when/text-version) in v1; captured
  digital signature is a later enhancement.
- **No hard deletes**; versioned edits + audit log; per-user attribution.

## Pending external input (not blocking the build)
- **Discount computation**: confirm exact mechanics and documentation with the
  clinic's accountant / BIR; watch pending "discount on top of promo"
  legislation. Logic is built configurable.
- **Privacy/compliance review**: data residency + retention specifics for the
  Data Privacy Act (engineering defaults set below; needs a compliance sign-off).
- **Medical-history default question set**: confirm the shipped default set
  against the clinic's current PDA intake form.

## Engineering defaults applied
- Backups: rely on Neon's managed backups + point-in-time recovery, ~30-day
  retention; nearest region is Singapore (`ap-southeast-1`; none in PH).
- Authorization enforced server-side per route via the role model (no separate
  read-model unless needed).

## Next step
Planning is resolved enough to start building. Authentication (JWT + the two
roles) is **implemented**; proposed next deliverables: the **patient + intake**
slice from `data-model.md`, and/or **wireframes** for the core screens (patient
record, dental chart, visit/billing).

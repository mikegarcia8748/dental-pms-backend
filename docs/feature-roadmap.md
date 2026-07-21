# Feature Roadmap

Built in slices so there is something usable early. Stack: Firebase
(App Hosting + SQL Connect/PostgreSQL + Auth) — see `tech-architecture.md`.

## Phase 1 — MVP
Digitizes workflow steps 2–7 with the now-required multi-user + billing depth.
- **Auth + two roles** (dentist full; staff read-only patient list/minor
  details), enforced via Firebase Auth claims + SQL Connect `@auth`
- **Audit trail** + per-user attribution; no hard deletes
- Patient profiles + search, with **structured intake**: allergies, configurable
  medical history, consent capture, senior/PWD + TIN fields
- Visit/encounter records; **treatment plans** for multi-visit cases
- Diagnosis notes
- **PDA dental chart**: current condition + planned-treatment overlay; age-based
  three-way dentition default with manual toggle; per-tooth notes
- Procedure entry (can pre-populate from planned-treatment `P` flags)
- **Billing**: editable line items (incl. non-procedure lines), configurable
  mandated discounts (senior/PWD) + VAT exemption, computed totals, bill state
  machine
- **Patient ledger**: carried balances + partial/installment payments
- Patient history view
- Privacy basics: encryption, access logging, backups

## Phase 2 — Next
- Procedure catalog (default types + prices)
- Installment schedules (planned due dates/amounts) if not in v1
- Printable/exportable bills + official receipts with discount breakdown
- Chart history timeline (per-tooth condition changes over time)
- Refunds / write-offs / credit notes

## Phase 3 — Later
- Reception / waiting queue (workflow step 1)
- Appointment scheduling
- Multiple full-access dentists + per-dentist attribution
- Reports: daily revenue, outstanding balances, discount/VAT summaries
- Insurance / PhilHealth handling

## Sequencing notes
- RBAC and audit are now Phase 1 (not deferrable) because staff can sign in.
- Billing depth (plans, ledger, discounts) is Phase 1 because the clinic
  confirmed installments and multi-visit cases are normal.
- Chart format is locked, so the chart editor can be built in Phase 1.

# Billing and Payments

Covers multi-visit treatments, installments, carried balances, and the
Philippine mandated discounts. Entities referenced here are detailed in
`data-model.md`.

> DISCLAIMER: The discount/VAT rules below are a development summary, not legal
> or accounting advice. Confirm exact computation, documentation, and any
> legislative changes with the clinic's accountant / BIR.

## Treatment plans (multi-visit cases)
Major treatments span multiple visits (e.g. impacted wisdom tooth removal).
A **TREATMENT_PLAN** (case) groups the related visits, procedures, and charges
so they can be tracked and billed together rather than as disconnected visits.

## Patient account / ledger and installments
Patients carry balances and pay in installments, so billing is **not** a simple
one-bill-one-payment model:
- Each patient has a running **balance** = sum of billed amounts − sum of
  payments (modeled via a patient-level ledger / account).
- Payments can be **partial** and made across later visits.
- Optional **installment schedule**: planned due amounts/dates against a plan or
  the patient account.
- Support overpayment/change, refunds, voids, and write-offs as ledger entries
  rather than edits to past records.

## Bills and line items
- A bill is generated from the procedures done, but **bill line items are
  independent of procedures**: a line can be a procedure, a consultation fee,
  materials, or a manual entry. A procedure can also be non-billed (warranty
  redo, free post-op check).
- Each line has an **editable unit price** (workflow step 6).
- Bill `total` is **computed** from line items + discount + VAT — never stored as
  an independent number that can drift.

## Bill state machine
`draft → finalized → (partially paid) → paid` with `void` as a terminal state.
- Prices are freely editable while `draft`.
- After `finalized`, changes are corrections (new versioned entries / credit
  notes), not silent overwrites.
- Payments only attach to `finalized` bills (or the patient account).

## Mandated discounts (PH)
Senior citizens (RA 9994) and PWDs (RA 10754) are entitled to a **20% discount
and VAT exemption** on dental services.

### Computation order
1. Determine the **VAT-exclusive base** (if the price is VAT-inclusive, strip the
   12% VAT first).
2. The covered service is **VAT-exempt** — do not add VAT.
3. Apply the **20% discount** to the VAT-exclusive base.

Worked example (VAT-inclusive posted price ₱1,120): strip VAT → ₱1,000 base →
less 20% → **₱800** due.

### Rules to encode
- **No double discount (current rule)**: the patient gets the **higher** of the
  statutory 20% or any promotional discount — not both. A patient who is both
  senior and PWD uses **one** ID (not two 20%s).
- There is **pending legislation** that would make the statutory discount apply
  *on top of* promotional offers; not yet law as of mid-2026 — keep the discount
  logic **configurable** so this can change without a rewrite.
- **Records for tax deduction**: capture the patient's name, **OSCA / PWD ID
  number**, and **TIN** (if applicable) on the transaction. Store senior/PWD
  status + ID number on the patient and snapshot it on the bill.

### Configurability
The clinic wants this configurable, so model discounts as a small rule set:
discount type (none / senior / pwd / promotional / custom), percentage, whether
it triggers VAT exemption, and stacking behavior — rather than hardcoding 20%.

## Tax
- VAT rate field (currently 12%) configurable.
- VAT-exempt flag per line/bill driven by the discount type.

## Decided / open items
- **Bill scope** (confirmed): billed **per visit**, optionally linked to a
  treatment plan for grouping; plan-level statement view later.
- **Refunds / write-offs** (confirmed): Phase 2, as ledger adjustment entries.
- Open (external): confirm exact discount computation with the clinic's
  accountant / BIR.

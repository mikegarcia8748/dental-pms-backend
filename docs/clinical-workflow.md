# Clinical Workflow

The clinic's existing manual flow, annotated with how the system supports each
step. Steps 2–7 happen within a single **Visit** record.

## Manual flow (as described by the clinic)
1. Patient is called by the receptionist.
2. Dentist talks to the patient.
   - If new: create a new patient profile.
   - If returning: search records and review previous diagnoses to determine
     whether this is a follow-up.
3. Patient setup done → proceed to procedure.
4. After the procedure, update the dental chart.
5. Enter the procedures done → create billing.
6. Modify the final price per procedure if needed.
7. Record payment.

## System mapping
| Step | System support | Notes |
|------|----------------|-------|
| 1. Receptionist calls patient | Out of scope for v1 (no reception/queue module) | Reception / waiting-queue is a later phase; the clinical flow below is the dentist's. |
| 2. New vs. returning patient | Patient create + search; history view | Returning patients open to their visit timeline + chart. |
| 3. Proceed to procedure | Start a new Visit for the patient | Visit carries date + chief complaint. |
| 4. Update dental chart | PDA odontogram editor | Per-tooth condition + side note. See chart spec. |
| 5. Enter procedures + create billing | Procedure entry → auto-generate Bill | Bill line items created from procedures; non-procedure lines allowed. |
| 6. Modify final price | Editable price per bill line item; apply discounts/VAT | Senior/PWD discount + VAT exemption (configurable). See billing doc. |
| 7. Record payment | Payment against the Bill / patient ledger | Partial + installment payments; balance carried at patient level. |

## The Visit as the connective record
A Visit ties together, for one date:
- the diagnosis/diagnoses entered,
- the procedures performed,
- the dental-chart changes made,
- the bill generated and the payments recorded.

This is what lets the dentist answer "what happened on this date?" and review
follow-up history cleanly.

## Multi-visit treatments (treatment plans)
Major cases (e.g. impacted wisdom tooth removal) span several visits. A
**Treatment Plan** groups those visits so procedures and charges are tracked and
can be billed together, and the patient's balance carries across visits via the
ledger. See `billing-and-payments.md` and `data-model.md`.

## Chart ↔ procedure ↔ billing bridge
The "Planned treatment" tooth condition (code `P`) is the bridge between the
chart and the rest of the flow:
- A tooth tagged `P` is effectively a queued procedure.
- At step 5, the procedure entry screen can pre-populate candidates from teeth
  tagged `P`.
- Completing a procedure flips the tooth's condition (e.g. to `F` filled,
  `C` crown, or `X` extracted).
- The completed procedure becomes a bill line item with an editable price.

(Detailed visit → procedure → bill → payment mapping to be expanded in a
follow-up session.)

# Access Control and Roles

> This supersedes the earlier "single persona, no RBAC" decision. The system now
> has **two roles**.

## Roles
### Dentist (full access)
Everything: create/edit patients, medical history, diagnoses, dental chart,
procedures, treatment plans, billing, discounts, payments, and viewing all
history.

### Staff / Assistant (limited, read-only)
View-only access to the **patient list and minor details**. Cannot create or
edit anything, and cannot see sensitive clinical or financial data.

#### Visible to staff (confirmed)
- Patient name
- Contact number
- Visit / appointment dates

#### Hidden from staff (confirmed)
- Medical history and allergies
- Diagnoses
- Dental chart / tooth conditions and notes
- Billing, balances, payments, discounts (incl. outstanding balance)

> Confirmed with the clinic: staff see name, contact, and visit/appointment
> dates only. Everything clinical and financial is hidden, enforced server-side
> (not just in the UI).

## Enforcement
- Identity via **Firebase Authentication**.
- Role stored as a **custom claim** on the auth user (e.g. `role: "dentist"` /
  `role: "staff"`), mirrored in the `USER` table for app use.
- Authorization enforced server-side using SQL Connect **`@auth` directives** on
  each query/mutation — staff-allowed queries return only the permitted fields;
  sensitive queries/mutations require the dentist role.
- Do **not** rely on hiding fields in the UI alone; the field-level restriction
  must be enforced in the query definitions.

## Accountability
Because more than one person can sign in, every create/edit/delete records the
acting user. See `AUDIT_LOG` in `data-model.md`. No shared logins — each person
authenticates as themselves.

## Future
Multi-dentist support (multiple full-access practitioners, per-dentist
attribution of procedures/bills) is a later phase; the `USER` + role model and
`performed_by` / `recorded_by` fields are designed so it can be added without a
rework.

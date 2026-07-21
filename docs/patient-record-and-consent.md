# Patient Record and Consent

The clinic wants **industry-standard** patient intake (medical history, consent)
that is **configurable**. This aligns the record with the official PDA patient
form rather than collapsing everything into one free-text field.

## Identity and demographics
Name, date of birth (drives the dental chart's age-based dentition default),
sex, contact details, address, occupation/nationality as needed.

### Minors
Capture **guardian** details (name, relationship, contact) when the patient is a
minor.

### Senior / PWD status (for billing)
`is_senior` / `is_pwd` flags + ID number (OSCA / PWD) + TIN if applicable. Used
by the discount logic in `billing-and-payments.md`.

## Allergies (structured, safety-critical)
Allergies — especially to anesthetics — must be **structured and prominent**
(not buried in free text), since they affect treatment safety. Surface them
clearly on the patient's record and at the start of a procedure.

## Medical history (configurable questionnaire)
Implement the standard medical-history questions (heart conditions, diabetes,
bleeding disorders, pregnancy, current medications, prior surgeries/
hospitalization, attending physician, etc.) as a **configurable question set**:
- `MEDICAL_HISTORY_QUESTION` — template of questions (editable/extendable).
- `PATIENT_MEDICAL_ANSWER` — patient's answers keyed to the question template.

This lets the clinic adjust the form over time without code changes, and keeps
answers structured/queryable.

## Informed consent
Capture consent as **recorded acknowledgments**, not just paper:
- `CONSENT` records: type (general treatment, radiograph, extraction/surgery,
  data-privacy), acknowledged timestamp, who acknowledged (patient/guardian),
  and the version of the consent text shown.
- Extraction/surgical consent matters legally and ties into the multi-visit
  treatment plans (e.g. wisdom tooth removal).
- A **data-privacy consent** supports the Data Privacy Act basis for processing
  (see `tech-architecture.md`).

## Configurability principle
"Industry standard but configurable" → ship a sensible default question set and
consent texts, but store them as data (templates/versions) so the clinic can
tailor them. Keep historical answers/consents tied to the version that was
actually presented.

## Decided / open items
- **Full digital intake at launch** (confirmed): allergies, medical-history
  questionnaire, and consent are all captured in the system from day one.
- **Consent**: recorded acknowledgment in v1; digital signature is a later
  enhancement.
- Open: confirm the default medical-history question set against the clinic's
  current PDA intake form.

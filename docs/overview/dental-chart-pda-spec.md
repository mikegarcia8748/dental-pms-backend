# Dental Chart Specification — PDA Format

The dental chart follows the official **Philippine Dental Association (PDA)**
format, using FDI/ISO two-digit notation laid out anatomically.

> **Authoritative source.** The official PDA "Dental Chart" form is preserved in
> the repo at [`reference/PDA-Dental-Chart.pdf`](reference/PDA-Dental-Chart.pdf)
> (4 pages: Patient Information Record + Dental/Medical History, Informed Consent,
> Dental Record Chart / Intraoral Examination, Treatment Record). The clinic's
> dentist emphasizes conformance to this form. The legend and exam sections below
> are transcribed directly from it.

## Layout
Both dentitions are represented, split by the **interdental occlusal line** into
upper (maxillary) and lower (mandibular) jaws.

- **Permanent teeth** = outer rows, quadrants 1–4.
- **Primary / deciduous teeth** = nested inner rows, quadrants 5–8.

Row order (left → right as displayed):

```
Maxillary arch (upper jaw)
  Primary:    55 54 53 52 51 | 61 62 63 64 65
  Permanent:  18 17 16 15 14 13 12 11 | 21 22 23 24 25 26 27 28
--- interdental occlusal line ---
  Permanent:  48 47 46 45 44 43 42 41 | 31 32 33 34 35 36 37 38
  Primary:    85 84 83 82 81 | 71 72 73 74 75
Mandibular arch (lower jaw)
```

On the physical form each tooth is drawn with a **STATUS** box (row of cells
labelled RIGHT ↔ LEFT) above/below the tooth glyph, and the tooth itself is an
occlusal-surface circle. The chart header captures Name, Age, Gender, Date.

## FDI numbering
Two digits: first = quadrant, second = tooth position from midline.

| Quadrant | Dentition | Side / jaw |
|----------|-----------|------------|
| 1 | Permanent | Upper right |
| 2 | Permanent | Upper left |
| 3 | Permanent | Lower left |
| 4 | Permanent | Lower right |
| 5 | Primary | Upper right |
| 6 | Primary | Upper left |
| 7 | Primary | Lower left |
| 8 | Primary | Lower right |

Primary vs. permanent is derivable from the quadrant digit (5–8 = primary).

## Current condition vs. planned treatment (a tooth can hold both)
A tooth's **current condition** and a **planned treatment** are stored
separately, so a tooth can show both at once — e.g. a crowned tooth (`JC`) that
now needs a root canal can be flagged as planned without erasing the crown.
- Current condition: the charted status/restoration code(s) per tooth, from
  `TOOTH_CONDITION_EVENT` (append-only, latest = current).
- Planned treatment: zero or more `PLANNED_TREATMENT` records per tooth,
  rendered as an **overlay/badge** on top of the condition.
- Completing a planned treatment creates a `PROCEDURE` and updates the current
  condition (e.g. a planned filling done → new event `Am`/`Co`).

See `data-model.md` for both structures.

## Condition legend — official PDA codes

> **Supersedes the earlier simplified set.** A previous iteration used a
> single-letter, per-tooth set `H / D / F / X / C / I / P` (recorded as
> "confirmed" in `open-questions-and-decisions.md`). The **official PDA form uses
> the fuller notation below**, split into three groups. The dentist's emphasis on
> the official form governs. `open-questions-and-decisions.md` and `data-model.md`
> should be updated to reference these codes when the chart slice is built.

Codes are transcribed verbatim from the form's legend. A tooth may carry a
condition marker plus one or more restoration/prosthetic or surgery markers.

### Condition
| Code | Meaning |
|------|---------|
| `✓` | Present Teeth |
| `D` | Decayed (Caries Indicated for Filling) |
| `M` | Missing due to Caries |
| `MO` | Missing due to Other Causes |
| `Im` | Impacted Tooth |
| `Sp` | Supernumerary Tooth |
| `Rf` | Root Fragment |
| `Un` | Unerupted |

### Restorations & Prosthetics
| Code | Meaning |
|------|---------|
| `Am` | Amalgam Filling |
| `Co` | Composite Filling |
| `JC` | Jacket Crown |
| `Ab` | Abutment |
| `Att` | Attachment |
| `P` | Pontic |
| `In` | Inlay |
| `Imp` | Implant |
| `S` | Sealants |
| `Rm` | Removable Denture |

### Surgery
| Code | Meaning |
|------|---------|
| `X` | Extraction due to Caries |
| `XO` | Extraction due to Other Causes |

Each tooth also carries a free-text **side note** (e.g. "sensitive to cold",
"refer to endo").

## Whole-mouth examination sections (from the form)
Beyond per-tooth marks, the chart records these examination findings:

- **X-ray Taken**: Periapical (with tooth no.), Panoramic, Cephalometric,
  Occlusal (Upper/Lower), Others.
- **Periodontal Screening**: Gingivitis, Early Periodontitis, Moderate
  Periodontitis, Advanced Periodontitis.
- **Occlusion**: Class (Molar), Overjet, Overbite, Midline Deviation, Crossbite.
- **Appliances**: Orthodontic, Stayplate, Others.
- **TMD**: Clenching, Clicking, Trismus, Muscle Spasm.

These are per-visit intraoral-examination findings (not per-tooth); model them on
the `VISIT`/examination record when the chart slice is built.

## Dentition display behavior
- **Default by age (three-way)**: adults open to permanent only; young children
  to primary only; the mixed-dentition range (~6–12) defaults to **Both**.
- **Manual toggle**: `Permanent` / `Both` / `Primary`, so the dentist can reveal
  either dentition regardless of age — covers a retained primary tooth still
  present in an adult.

## Chart → procedure → billing bridge
- A tooth with a **planned treatment** is a queued procedure.
- The procedure entry screen can pre-populate from planned-treatment teeth.
- Completing a procedure updates the tooth's current condition (e.g. planned
  filling → `Am`/`Co`; planned extraction → `X`/`XO`) and creates a bill line
  item. This mirrors the form's page-4 **Treatment Record** row
  (`Date | Tooth No. | Procedure | Dentist | Amount charged | Amount Paid |
  Balance | Next Appt.`).

## Prototype color mapping (reference, optional)
Color is a secondary cue only — the code text is always shown so the chart is not
color-dependent. Suggested families: healthy/present = green; decay = red;
fillings (`Am`/`Co`) = blue; missing/extracted (`M`/`MO`/`X`/`XO`) = gray dashed;
crowns/prosthetics (`JC`/`P`/`Rm`) = purple; implants (`Imp`) = teal; planned
overlay = amber. Final palette is a UI decision.

## Storage
Charted state lives in the append-only `TOOTH_CONDITION_EVENT` log (see
`data-model.md`). Each condition change is a dated entry (code + note + recorded_by
+ recorded_at) so the chart has full per-tooth history; current = latest event per
tooth. Whole-mouth exam sections attach to the visit/examination record.

# Dental Chart Specification — PDA Format

The dental chart follows the official **Philippine Dental Association (PDA)**
format, using FDI/ISO two-digit notation laid out anatomically.

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
separately, so a tooth can show both at once — e.g. a crowned tooth (`C`) that
now needs a root canal can be flagged `P` without erasing the `C`.
- Current condition: one code per tooth, from `TOOTH_CONDITION_EVENT`
  (append-only, latest = current).
- Planned treatment: zero or more `PLANNED_TREATMENT` records per tooth,
  rendered as an **overlay/badge** on top of the condition color.
- Completing a planned treatment creates a `PROCEDURE` and updates the current
  condition (e.g. `P` done → new event `F` / `C` / `X`).

See `data-model.md` for both structures.

## Condition legend
One **current condition** code per tooth (per-tooth, NOT per-surface — this
simplifies the data model). `P` (planned treatment) is an overlay, not a current
condition. Proposed letter codes (confirm letters against the physical form):

| Code | Condition |
|------|-----------|
| `H` | Healthy |
| `D` | Acid decay (cavity) |
| `F` | Filled (amalgam / composite) |
| `X` | Extracted / missing |
| `C` | Porcelain crown |
| `I` | Titanium implant |
| `P` | Planned treatment |

Each tooth also carries a free-text **side note** (e.g. "sensitive to cold",
"refer to endo").

> NOTE: The official PDA chart PDF on the PDA site is a scanned image with no
> extractable text. The meanings came directly from the clinic, and the
> single-letter codes (H / D / F / X / C / I / P) are **confirmed** for use.

## Dentition display behavior
- **Default by age (three-way)**: adults open to permanent only; young children
  to primary only; the mixed-dentition range (~6–12) defaults to **Both**.
- **Manual toggle**: `Permanent` / `Both` / `Primary`, so the dentist can reveal
  either dentition regardless of age — covers a retained primary tooth still
  present in an adult.

## Chart → procedure → billing bridge
- A tooth tagged `P` (planned treatment) is a queued procedure.
- The procedure entry screen can pre-populate from teeth tagged `P`.
- Completing a procedure flips the tooth condition (e.g. `P` → `F` / `C` / `X`)
  and creates a bill line item.

## Prototype color mapping (reference)
Used in the interactive mockup; color is a secondary cue — the letter code is
always shown so the chart is not color-dependent.

| Code | Color family |
|------|--------------|
| `H` | Green (success) |
| `D` | Red (danger) |
| `F` | Blue (accent) |
| `X` | Gray, dashed border (missing) |
| `C` | Purple |
| `I` | Teal |
| `P` | Amber (pending) |

## Storage
Charted state lives in `TOOTH_RECORD` (see `data-model.md`). Each condition
change is a dated entry (code + note + date) so the chart has full history.

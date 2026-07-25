-- Patient registration & intake schema: patients, structured allergies, a configurable
-- medical/dental intake questionnaire, recorded consents, and a cross-cutting audit log.
-- Mirrors the official PDA "Patient Information Record" (see docs/reference/PDA-Dental-Chart.pdf).

-- A patient of the clinic. `registered_at` is the clinically meaningful (and, for legacy
-- paper patients, backdatable) join date; `created_at` is the immutable row-insert time.
CREATE TABLE patient (
    id                              UUID         PRIMARY KEY,
    last_name                       VARCHAR(100) NOT NULL,
    first_name                      VARCHAR(100) NOT NULL,
    middle_name                     VARCHAR(100),
    suffix                          VARCHAR(20),
    nickname                        VARCHAR(100),
    date_of_birth                   DATE,                 -- nullable so sparse legacy records can be entered
    sex                             VARCHAR(10)  NOT NULL,
    religion                        VARCHAR(100),
    nationality                     VARCHAR(100),
    civil_status                    VARCHAR(30),
    occupation                      VARCHAR(150),
    address                         TEXT,
    mobile_number                   VARCHAR(40),          -- primary contact; relaxed for legacy
    home_number                     VARCHAR(40),
    office_number                   VARCHAR(40),
    email                           VARCHAR(320),
    guardian_name                   VARCHAR(200),         -- required for minors (enforced in validation)
    guardian_relationship           VARCHAR(60),
    guardian_occupation             VARCHAR(150),
    guardian_contact                VARCHAR(40),
    emergency_contact_name          VARCHAR(200),
    emergency_contact_relationship  VARCHAR(60),
    emergency_contact_number        VARCHAR(40),
    is_senior                       BOOLEAN      NOT NULL DEFAULT FALSE,
    is_pwd                          BOOLEAN      NOT NULL DEFAULT FALSE,
    sc_pwd_id_number                VARCHAR(60),          -- OSCA / PWD ID for mandated discounts
    tin                             VARCHAR(30),
    dental_insurance                VARCHAR(200),         -- capture-only; no claim processing in v1
    insurance_effective_date        DATE,
    referral_source                 VARCHAR(200),         -- "Whom may we thank for referring you?"
    is_legacy                       BOOLEAN      NOT NULL DEFAULT FALSE,
    legacy_summary                  TEXT,                 -- narrative of the old paper history
    registered_at                   TIMESTAMP    NOT NULL,
    active                          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by                      UUID         NOT NULL REFERENCES app_user (id),
    created_at                      TIMESTAMP    NOT NULL,
    updated_by                      UUID         REFERENCES app_user (id),
    updated_at                      TIMESTAMP
);

CREATE INDEX idx_patient_name ON patient (last_name, first_name);
CREATE INDEX idx_patient_active ON patient (active);

-- Structured, safety-critical allergies (esp. anesthetics). Surfaced prominently on the record.
CREATE TABLE allergy (
    id          UUID         PRIMARY KEY,
    patient_id  UUID         NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    substance   VARCHAR(150) NOT NULL,
    severity    VARCHAR(20),                              -- MILD | MODERATE | SEVERE
    note        TEXT,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,        -- soft-delete; no hard deletes
    recorded_by UUID         NOT NULL REFERENCES app_user (id),
    recorded_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_allergy_patient ON allergy (patient_id);

-- Configurable intake questionnaire template covering both MEDICAL and DENTAL history.
-- Editing later = insert a new (code, version) row and deactivate the old, so answers keep
-- pointing at the exact version presented. Seeded from the PDA form in V3.
CREATE TABLE intake_question (
    id            UUID        PRIMARY KEY,
    section       VARCHAR(10) NOT NULL,                   -- MEDICAL | DENTAL
    code          VARCHAR(80) NOT NULL,                   -- stable machine key, e.g. 'cond_diabetes'
    prompt        TEXT        NOT NULL,
    answer_type   VARCHAR(10) NOT NULL,                   -- BOOLEAN | TEXT | DATE | CHOICE
    choices       TEXT,                                   -- JSON array for CHOICE, else null
    display_order INT         NOT NULL,
    version       INT         NOT NULL DEFAULT 1,
    active        BOOLEAN     NOT NULL DEFAULT TRUE,
    UNIQUE (code, version)
);

CREATE INDEX idx_intake_question_section ON intake_question (section, display_order);

-- A patient's answer to one intake question. One current answer per (patient, question);
-- updates overwrite and history is captured in audit_log. Value lives in the typed column
-- matching the question's answer_type.
CREATE TABLE patient_intake_answer (
    id             UUID      PRIMARY KEY,
    patient_id     UUID      NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    question_id    UUID      NOT NULL REFERENCES intake_question (id),
    answer_boolean BOOLEAN,
    answer_text    TEXT,
    answer_date    DATE,
    recorded_by    UUID      NOT NULL REFERENCES app_user (id),
    recorded_at    TIMESTAMP NOT NULL,
    UNIQUE (patient_id, question_id)
);

CREATE INDEX idx_answer_patient ON patient_intake_answer (patient_id);

-- Reference/seed table holding the current consent-text bodies and their versions.
CREATE TABLE consent_text (
    id      UUID         PRIMARY KEY,
    type    VARCHAR(20)  NOT NULL,                        -- TREATMENT | RADIOGRAPH | EXTRACTION | DATA_PRIVACY
    version VARCHAR(40)  NOT NULL,
    title   VARCHAR(200) NOT NULL,
    body    TEXT         NOT NULL,
    active  BOOLEAN      NOT NULL DEFAULT TRUE,
    UNIQUE (type, version)
);

-- A recorded consent acknowledgment (v1 = acknowledgment, not a captured signature).
CREATE TABLE consent (
    id                   UUID        PRIMARY KEY,
    patient_id           UUID        NOT NULL REFERENCES patient (id) ON DELETE CASCADE,
    type                 VARCHAR(20) NOT NULL,            -- TREATMENT | RADIOGRAPH | EXTRACTION | DATA_PRIVACY
    text_version         VARCHAR(40) NOT NULL,            -- which consent_text version was shown
    acknowledged_by_role VARCHAR(20) NOT NULL,            -- PATIENT | GUARDIAN
    acknowledged_by_name VARCHAR(200),
    acknowledged_at      TIMESTAMP   NOT NULL,
    recorded_by          UUID        NOT NULL REFERENCES app_user (id),
    recorded_at          TIMESTAMP   NOT NULL
);

CREATE INDEX idx_consent_patient ON consent (patient_id);

-- Cross-cutting audit trail: every create/edit/deactivate records the acting user.
CREATE TABLE audit_log (
    id        UUID        PRIMARY KEY,
    user_id   UUID        NOT NULL REFERENCES app_user (id),
    action    VARCHAR(20) NOT NULL,                       -- CREATE | UPDATE | DEACTIVATE
    entity    VARCHAR(40) NOT NULL,                       -- patient | allergy | intake_answer | consent
    entity_id UUID        NOT NULL,
    at        TIMESTAMP   NOT NULL
);

CREATE INDEX idx_audit_entity ON audit_log (entity, entity_id);

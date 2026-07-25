-- Seed data transcribed from the official PDA "Patient Information Record" (page 1) and
-- "Informed Consent" (page 2). See docs/reference/PDA-Dental-Chart.pdf.
-- Question ids are generated at migration time (stable thereafter); clients fetch them via
-- GET /intake-questions and submit answers keyed by id. version/active use column defaults.

-- ── MEDICAL history questions (PDA page 1) ────────────────────────────────────────────────
INSERT INTO intake_question (id, section, code, prompt, answer_type, display_order) VALUES
    (gen_random_uuid(), 'MEDICAL', 'physician_name',            'Name of physician',                                          'TEXT',    1),
    (gen_random_uuid(), 'MEDICAL', 'physician_specialty',       'Physician''s specialty (if applicable)',                     'TEXT',    2),
    (gen_random_uuid(), 'MEDICAL', 'physician_office_number',   'Physician''s office number',                                 'TEXT',    3),
    (gen_random_uuid(), 'MEDICAL', 'good_health',               'Are you in good health?',                                    'BOOLEAN', 4),
    (gen_random_uuid(), 'MEDICAL', 'under_medical_treatment',   'Are you under medical treatment now?',                       'BOOLEAN', 5),
    (gen_random_uuid(), 'MEDICAL', 'medical_treatment_condition','If under treatment, what condition is being treated?',      'TEXT',    6),
    (gen_random_uuid(), 'MEDICAL', 'serious_illness_surgery',   'Have you ever had a serious illness or surgical operation?', 'BOOLEAN', 7),
    (gen_random_uuid(), 'MEDICAL', 'serious_illness_details',   'If yes, what illness or operation?',                         'TEXT',    8),
    (gen_random_uuid(), 'MEDICAL', 'hospitalized',              'Have you ever been hospitalized?',                           'BOOLEAN', 9),
    (gen_random_uuid(), 'MEDICAL', 'hospitalized_details',      'If yes, when and why?',                                      'TEXT',    10),
    (gen_random_uuid(), 'MEDICAL', 'taking_medication',         'Are you taking any prescription or non-prescription medication?', 'BOOLEAN', 11),
    (gen_random_uuid(), 'MEDICAL', 'medication_details',        'If yes, please specify',                                     'TEXT',    12),
    (gen_random_uuid(), 'MEDICAL', 'uses_tobacco',              'Do you use tobacco products?',                               'BOOLEAN', 13),
    (gen_random_uuid(), 'MEDICAL', 'uses_alcohol_drugs',        'Do you use alcohol, cocaine, or other dangerous drugs?',     'BOOLEAN', 14),
    (gen_random_uuid(), 'MEDICAL', 'bleeding_time',             'Bleeding time (if known)',                                   'TEXT',    15),
    (gen_random_uuid(), 'MEDICAL', 'blood_type',                'Blood type',                                                 'TEXT',    16),
    (gen_random_uuid(), 'MEDICAL', 'blood_pressure',            'Blood pressure',                                             'TEXT',    17),
    (gen_random_uuid(), 'MEDICAL', 'is_pregnant',               'For women: Are you pregnant?',                               'BOOLEAN', 18),
    (gen_random_uuid(), 'MEDICAL', 'is_nursing',                'For women: Are you nursing?',                                'BOOLEAN', 19),
    (gen_random_uuid(), 'MEDICAL', 'birth_control_pills',       'For women: Are you taking birth control pills?',             'BOOLEAN', 20),
    -- Q13: "Do you have or have you had any of the following?" — one boolean per condition.
    (gen_random_uuid(), 'MEDICAL', 'cond_high_blood_pressure',  'High blood pressure',                                        'BOOLEAN', 21),
    (gen_random_uuid(), 'MEDICAL', 'cond_low_blood_pressure',   'Low blood pressure',                                         'BOOLEAN', 22),
    (gen_random_uuid(), 'MEDICAL', 'cond_epilepsy_convulsions', 'Epilepsy / Convulsions',                                     'BOOLEAN', 23),
    (gen_random_uuid(), 'MEDICAL', 'cond_aids_hiv',             'AIDS or HIV infection',                                      'BOOLEAN', 24),
    (gen_random_uuid(), 'MEDICAL', 'cond_std',                  'Sexually transmitted disease',                               'BOOLEAN', 25),
    (gen_random_uuid(), 'MEDICAL', 'cond_stomach_ulcers',       'Stomach troubles / Ulcers',                                  'BOOLEAN', 26),
    (gen_random_uuid(), 'MEDICAL', 'cond_fainting_seizure',     'Fainting seizure',                                           'BOOLEAN', 27),
    (gen_random_uuid(), 'MEDICAL', 'cond_rapid_weight_loss',    'Rapid weight loss',                                          'BOOLEAN', 28),
    (gen_random_uuid(), 'MEDICAL', 'cond_radiation_therapy',    'Radiation therapy',                                          'BOOLEAN', 29),
    (gen_random_uuid(), 'MEDICAL', 'cond_joint_replacement',    'Joint replacement / Implant',                                'BOOLEAN', 30),
    (gen_random_uuid(), 'MEDICAL', 'cond_heart_surgery',        'Heart surgery',                                              'BOOLEAN', 31),
    (gen_random_uuid(), 'MEDICAL', 'cond_heart_attack',         'Heart attack',                                               'BOOLEAN', 32),
    (gen_random_uuid(), 'MEDICAL', 'cond_thyroid_problem',      'Thyroid problem',                                            'BOOLEAN', 33),
    (gen_random_uuid(), 'MEDICAL', 'cond_heart_disease',        'Heart disease',                                              'BOOLEAN', 34),
    (gen_random_uuid(), 'MEDICAL', 'cond_heart_murmur',         'Heart murmur',                                               'BOOLEAN', 35),
    (gen_random_uuid(), 'MEDICAL', 'cond_hepatitis_liver',      'Hepatitis / Liver disease',                                  'BOOLEAN', 36),
    (gen_random_uuid(), 'MEDICAL', 'cond_rheumatic_fever',      'Rheumatic fever',                                            'BOOLEAN', 37),
    (gen_random_uuid(), 'MEDICAL', 'cond_hay_fever_allergies',  'Hay fever / Allergies',                                      'BOOLEAN', 38),
    (gen_random_uuid(), 'MEDICAL', 'cond_respiratory_problems', 'Respiratory problems',                                       'BOOLEAN', 39),
    (gen_random_uuid(), 'MEDICAL', 'cond_hepatitis_jaundice',   'Hepatitis / Jaundice',                                       'BOOLEAN', 40),
    (gen_random_uuid(), 'MEDICAL', 'cond_tuberculosis',         'Tuberculosis',                                               'BOOLEAN', 41),
    (gen_random_uuid(), 'MEDICAL', 'cond_swollen_ankles',       'Swollen ankles',                                             'BOOLEAN', 42),
    (gen_random_uuid(), 'MEDICAL', 'cond_kidney_disease',       'Kidney disease',                                             'BOOLEAN', 43),
    (gen_random_uuid(), 'MEDICAL', 'cond_diabetes',             'Diabetes',                                                   'BOOLEAN', 44),
    (gen_random_uuid(), 'MEDICAL', 'cond_chest_pain',           'Chest pain',                                                 'BOOLEAN', 45),
    (gen_random_uuid(), 'MEDICAL', 'cond_stroke',               'Stroke',                                                     'BOOLEAN', 46),
    (gen_random_uuid(), 'MEDICAL', 'cond_cancer_tumors',        'Cancer / Tumors',                                            'BOOLEAN', 47),
    (gen_random_uuid(), 'MEDICAL', 'cond_anemia',               'Anemia',                                                     'BOOLEAN', 48),
    (gen_random_uuid(), 'MEDICAL', 'cond_angina',               'Angina',                                                     'BOOLEAN', 49),
    (gen_random_uuid(), 'MEDICAL', 'cond_asthma',               'Asthma',                                                     'BOOLEAN', 50),
    (gen_random_uuid(), 'MEDICAL', 'cond_emphysema',            'Emphysema',                                                   'BOOLEAN', 51),
    (gen_random_uuid(), 'MEDICAL', 'cond_bleeding_problems',    'Bleeding problems',                                          'BOOLEAN', 52),
    (gen_random_uuid(), 'MEDICAL', 'cond_blood_diseases',       'Blood diseases',                                             'BOOLEAN', 53),
    (gen_random_uuid(), 'MEDICAL', 'cond_head_injuries',        'Head injuries',                                              'BOOLEAN', 54),
    (gen_random_uuid(), 'MEDICAL', 'cond_arthritis_rheumatism', 'Arthritis / Rheumatism',                                     'BOOLEAN', 55),
    (gen_random_uuid(), 'MEDICAL', 'other_conditions',          'Other conditions (please specify)',                          'TEXT',    56);

-- ── DENTAL history questions (PDA page 1) ─────────────────────────────────────────────────
INSERT INTO intake_question (id, section, code, prompt, answer_type, display_order) VALUES
    (gen_random_uuid(), 'DENTAL', 'previous_dentist',        'Previous dentist (Dr.)',                          'TEXT', 57),
    (gen_random_uuid(), 'DENTAL', 'last_dental_visit',       'Last dental visit',                               'TEXT', 58),
    (gen_random_uuid(), 'DENTAL', 'reason_for_consultation', 'What is your reason for dental consultation?',    'TEXT', 59);

-- ── Consent texts ─────────────────────────────────────────────────────────────────────────
-- TREATMENT = PDA page-2 informed consent (verify exact wording with the clinic before go-live).
INSERT INTO consent_text (id, type, version, title, body) VALUES
    (gen_random_uuid(), 'TREATMENT', 'PDA-2010', 'Informed Consent for Dental Treatment', $tx$INFORMED CONSENT

TREATMENT TO BE DONE: I understand and consent to have any treatment done by the dentist after the procedure, the risks, benefits and cost have been fully explained. These treatments include, but are not limited to, x-rays, cleanings, periodontal treatments, fillings, crowns, bridges, all types of extraction, root canals, and/or dentures, local anesthetics and surgical cases.

DRUGS & MEDICATIONS: I understand that antibiotics, analgesics and other medications can cause allergic reactions like redness and swelling of tissues, pain, itching, vomiting, and/or anaphylactic shock.

CHANGES IN TREATMENT PLAN: I understand that during treatment it may be necessary to change or add procedures because of conditions found while working on the teeth that were not discovered during examination. I give my permission to the dentist to make any/all changes and additions as necessary, with my responsibility to pay all the costs agreed.

RADIOGRAPH: I understand that a radiograph (x-ray) may be necessary as part of the diagnostic aid to come up with a tentative diagnosis of my dental problem and to make a good treatment plan.

REMOVAL OF TEETH: I understand that there are alternatives to tooth removal (root canal therapy, crowns, periodontal surgery, etc.) and I completely understand these alternatives, including their risks and benefits, prior to authorizing the dentist to remove teeth. I understand that removing teeth does not always remove all infections, if present, and that further treatment may be necessary.

CROWNS (CAPS) & BRIDGES: I understand that preparing a tooth may irritate the nerve tissue, leaving the tooth extra sensitive to heat, cold and pressure. I understand that it is sometimes not possible to match the color of natural teeth exactly with artificial teeth, and that I may wear temporary crowns which must be kept on until the permanent crowns are delivered.

ENDODONTICS (ROOT CANAL): I understand there is no guarantee that a root canal treatment will save a tooth and that complications can occur. I understand that referral to an endodontist may be necessary and that a tooth may still require removal in spite of all efforts to save it.

PERIODONTAL DISEASE: I understand that periodontal disease is a serious condition causing gum and bone inflammation and/or loss that can lead to the loss of my teeth, and I understand the alternative treatment plans to correct it, including gum surgery and tooth extraction with or without replacement.

FILLINGS: I understand that care must be exercised in chewing on fillings, especially during the first 24 hours, to avoid breakage. I understand that a more extensive filling or a crown may be required, and that sensitivity is a common but usually temporary after-effect of a newly placed filling.

DENTURES: I understand that wearing dentures can be difficult and that sore spots, altered speech and difficulty eating are common problems. I understand that it is my responsibility to return for delivery of dentures and that failure to keep my delivery appointment may result in poorly fitted dentures.

I understand that dentistry is not an exact science and that no dentist can properly guarantee accurate results all the time. I hereby authorize any of the doctors/dental auxiliaries to proceed with and perform the dental restorations and treatments as explained to me. I understand that these are subject to modification depending on undiagnosable circumstances that may arise during treatment. I understand that regardless of any dental insurance coverage I may have, I am responsible for payment of dental fees, and I agree to pay any attorney's fees, collection fee, or court costs incurred to satisfy any obligation to this office. All treatment was properly explained to me, and for any untoward circumstances that may arise during the procedure the attending dentist will not be held liable, since it is my free will, with full trust and confidence in him/her, to undergo dental treatment under his/her care.$tx$);

-- DATA_PRIVACY = processing basis under RA 10173 (not on the PDA form; required by law).
INSERT INTO consent_text (id, type, version, title, body) VALUES
    (gen_random_uuid(), 'DATA_PRIVACY', 'RA10173-v1', 'Data Privacy Consent (RA 10173)', $dp$DATA PRIVACY CONSENT (Republic Act No. 10173 — Data Privacy Act of 2012)

I consent to the collection, recording, storage, and processing by this dental clinic of my personal and sensitive personal information — including my identity and contact details, medical and dental history, allergies, diagnoses, treatments, radiographs, and billing records — for the purposes of providing dental care, maintaining my clinical record, processing billing and payments, and complying with legal and regulatory obligations.

I understand that my information will be kept confidential and secure, retained only for as long as necessary for these purposes or as required by law, and not shared with third parties except as necessary for my care, with my consent, or as required by law. I understand that I have the right to access and correct my information and to be informed of how it is processed, in accordance with the Data Privacy Act of 2012 (RA 10173) and its implementing rules and regulations.$dp$);

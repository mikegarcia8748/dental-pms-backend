package com.pms.dental.domain.model

/** A patient together with their structured allergies, intake answers, and recorded consents. */
data class PatientDetails(
    val patient: Patient,
    val allergies: List<Allergy>,
    val answers: List<PatientIntakeAnswer>,
    val consents: List<Consent>,
)

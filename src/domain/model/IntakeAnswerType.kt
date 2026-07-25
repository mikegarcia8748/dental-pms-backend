package com.pms.dental.domain.model

/**
 * The shape of a question's answer. Determines which value column on a
 * [PatientIntakeAnswer] must be populated. `CHOICE` stores the selected option(s) as text.
 */
enum class IntakeAnswerType { BOOLEAN, TEXT, DATE, CHOICE }

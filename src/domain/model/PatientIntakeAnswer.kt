package com.pms.dental.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A patient's current answer to one [IntakeQuestion]. The value lives in the typed column that
 * matches the question's [IntakeAnswerType]; the others are null. One answer per (patient,
 * question) — re-answering overwrites, with history captured in the audit log.
 */
data class PatientIntakeAnswer(
    val id: UUID,
    val patientId: UUID,
    val questionId: UUID,
    val answerBoolean: Boolean?,
    val answerText: String?,
    val answerDate: LocalDate?,
    val recordedBy: UUID,
    val recordedAt: Instant,
)

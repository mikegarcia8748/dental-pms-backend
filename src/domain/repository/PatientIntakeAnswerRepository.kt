package com.pms.dental.domain.repository

import com.pms.dental.domain.model.PatientIntakeAnswer
import java.util.UUID

interface PatientIntakeAnswerRepository {
    /** Insert or overwrite each answer, one current answer per (patient, question). */
    suspend fun upsertAll(answers: List<PatientIntakeAnswer>)
    suspend fun listByPatient(patientId: UUID): List<PatientIntakeAnswer>
}

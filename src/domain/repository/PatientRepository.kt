package com.pms.dental.domain.repository

import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientIntakeAnswer
import java.util.UUID

interface PatientRepository {
    /**
     * Persist a whole registration — the patient plus its nested allergies, answers, consents,
     * and audit entries — in a **single transaction**, so a partial failure leaves nothing behind.
     */
    suspend fun insertRegistration(
        patient: Patient,
        allergies: List<Allergy>,
        answers: List<PatientIntakeAnswer>,
        consents: List<Consent>,
        audit: List<AuditEntry>,
    )

    suspend fun findById(id: UUID): Patient?
    suspend fun existsById(id: UUID): Boolean

    /** Name search (last/first, case-insensitive), newest first, paged. */
    suspend fun search(query: String, limit: Int, offset: Int, includeInactive: Boolean): List<Patient>
    suspend fun countSearch(query: String, includeInactive: Boolean): Long

    /** Replace the mutable columns (demographics, PH status, legacy, active, updated_*). */
    suspend fun update(patient: Patient)
}

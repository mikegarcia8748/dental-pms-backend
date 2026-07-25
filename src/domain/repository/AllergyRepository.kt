package com.pms.dental.domain.repository

import com.pms.dental.domain.model.Allergy
import java.util.UUID

interface AllergyRepository {
    suspend fun insert(allergy: Allergy)
    suspend fun findById(id: UUID): Allergy?
    suspend fun listByPatient(patientId: UUID, includeInactive: Boolean): List<Allergy>
    /** Replace substance/severity/note/active for an existing allergy. */
    suspend fun update(allergy: Allergy)
}

package com.pms.dental.domain.repository

import com.pms.dental.domain.model.Consent
import java.util.UUID

interface ConsentRepository {
    suspend fun insert(consent: Consent)
    suspend fun listByPatient(patientId: UUID): List<Consent>
}

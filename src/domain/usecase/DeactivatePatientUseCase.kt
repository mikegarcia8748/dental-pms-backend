package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface DeactivatePatientResult {
    data object Success : DeactivatePatientResult
    data object NotFound : DeactivatePatientResult
}

/**
 * Business rule: soft-delete a patient (no hard deletes). Idempotent — deactivating an
 * already-inactive patient succeeds without re-writing or re-auditing.
 */
class DeactivatePatientUseCase(
    private val patients: PatientRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(patientId: UUID, actingUserId: UUID): DeactivatePatientResult {
        val existing = patients.findById(patientId) ?: return DeactivatePatientResult.NotFound
        if (!existing.active) return DeactivatePatientResult.Success

        val now = clock.now()
        patients.update(existing.copy(active = false, updatedBy = actingUserId, updatedAt = now))
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.DEACTIVATE, "patient", patientId, now))
        return DeactivatePatientResult.Success
    }
}

package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface ReactivatePatientResult {
    data object Success : ReactivatePatientResult
    data object NotFound : ReactivatePatientResult
}

/**
 * Business rule: restore a soft-deleted patient (the inverse of deactivate). Idempotent —
 * reactivating an already-active patient succeeds without re-writing or re-auditing.
 */
class ReactivatePatientUseCase(
    private val patients: PatientRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(patientId: UUID, actingUserId: UUID): ReactivatePatientResult {
        val existing = patients.findById(patientId) ?: return ReactivatePatientResult.NotFound
        if (existing.active) return ReactivatePatientResult.Success

        val now = clock.now()
        patients.update(existing.copy(active = true, updatedBy = actingUserId, updatedAt = now))
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.REACTIVATE, "patient", patientId, now))
        return ReactivatePatientResult.Success
    }
}

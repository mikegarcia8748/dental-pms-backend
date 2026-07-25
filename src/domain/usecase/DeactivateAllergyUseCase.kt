package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface DeactivateAllergyResult {
    data object Success : DeactivateAllergyResult
    data object NotFound : DeactivateAllergyResult
}

/**
 * Business rule: soft-delete an allergy (no hard deletes). Must belong to the patient.
 * Idempotent — deactivating an already-inactive allergy succeeds without re-writing.
 */
class DeactivateAllergyUseCase(
    private val allergies: AllergyRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(patientId: UUID, allergyId: UUID, actingUserId: UUID): DeactivateAllergyResult {
        val existing = allergies.findById(allergyId) ?: return DeactivateAllergyResult.NotFound
        if (existing.patientId != patientId) return DeactivateAllergyResult.NotFound
        if (!existing.active) return DeactivateAllergyResult.Success

        val now = clock.now()
        allergies.update(existing.copy(active = false))
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.DEACTIVATE, "allergy", allergyId, now))
        return DeactivateAllergyResult.Success
    }
}

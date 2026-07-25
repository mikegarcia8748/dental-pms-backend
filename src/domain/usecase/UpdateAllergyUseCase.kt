package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.NewAllergy
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface UpdateAllergyResult {
    data class Success(val allergy: Allergy) : UpdateAllergyResult
    data object NotFound : UpdateAllergyResult
}

/**
 * Business rule: edit an allergy's substance/severity/note. The allergy must exist and belong to
 * the given patient (guards against editing another patient's record via a mismatched path).
 */
class UpdateAllergyUseCase(
    private val allergies: AllergyRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        patientId: UUID,
        allergyId: UUID,
        input: NewAllergy,
        actingUserId: UUID,
    ): UpdateAllergyResult {
        val existing = allergies.findById(allergyId) ?: return UpdateAllergyResult.NotFound
        if (existing.patientId != patientId) return UpdateAllergyResult.NotFound

        val now = clock.now()
        val updated = existing.copy(
            substance = input.substance.trim(),
            severity = input.severity,
            note = input.note?.trim(),
        )
        allergies.update(updated)
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.UPDATE, "allergy", allergyId, now))
        return UpdateAllergyResult.Success(updated)
    }
}

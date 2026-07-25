package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.NewAllergy
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface AddAllergyResult {
    data class Success(val allergy: Allergy) : AddAllergyResult
    data object PatientNotFound : AddAllergyResult
}

/** Business rule: add a structured allergy to an existing patient, attributed and audited. */
class AddAllergyUseCase(
    private val patients: PatientRepository,
    private val allergies: AllergyRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(patientId: UUID, input: NewAllergy, actingUserId: UUID): AddAllergyResult {
        if (!patients.existsById(patientId)) return AddAllergyResult.PatientNotFound

        val now = clock.now()
        val allergy = Allergy(
            id = idGenerator.newId(),
            patientId = patientId,
            substance = input.substance.trim(),
            severity = input.severity,
            note = input.note?.trim(),
            active = true,
            recordedBy = actingUserId,
            recordedAt = now,
        )
        allergies.insert(allergy)
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.CREATE, "allergy", allergy.id, now))
        return AddAllergyResult.Success(allergy)
    }
}

package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.NewConsent
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.ConsentRepository
import com.pms.dental.domain.repository.ConsentTextRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface RecordConsentResult {
    data class Success(val consent: Consent) : RecordConsentResult
    data class Rejected(val error: RecordConsentError) : RecordConsentResult
}

enum class RecordConsentError { PatientNotFound, UnknownConsentText }

/**
 * Business rule: record a consent acknowledgment for an existing patient. The consent must
 * reference a real consent-text (type, version). Captures who acknowledged, when, and which
 * version was shown; attributed to the recording dentist and audited.
 */
class RecordConsentUseCase(
    private val patients: PatientRepository,
    private val consentTexts: ConsentTextRepository,
    private val consents: ConsentRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(patientId: UUID, input: NewConsent, actingUserId: UUID): RecordConsentResult {
        if (!patients.existsById(patientId)) return RecordConsentResult.Rejected(RecordConsentError.PatientNotFound)
        if (consentTexts.find(input.type, input.textVersion) == null) {
            return RecordConsentResult.Rejected(RecordConsentError.UnknownConsentText)
        }

        val now = clock.now()
        val consent = Consent(
            id = idGenerator.newId(),
            patientId = patientId,
            type = input.type,
            textVersion = input.textVersion,
            acknowledgedByRole = input.acknowledgedByRole,
            acknowledgedByName = input.acknowledgedByName?.trim(),
            acknowledgedAt = input.acknowledgedAt ?: now,
            recordedBy = actingUserId,
            recordedAt = now,
        )
        consents.insert(consent)
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.CREATE, "consent", consent.id, now))
        return RecordConsentResult.Success(consent)
    }
}

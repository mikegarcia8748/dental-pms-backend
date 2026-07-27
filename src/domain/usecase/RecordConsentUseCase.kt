package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AcknowledgedByRole
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
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

sealed interface RecordConsentResult {
    data class Success(val consent: Consent) : RecordConsentResult
    data class Rejected(val error: RecordConsentError) : RecordConsentResult
}

enum class RecordConsentError {
    PatientNotFound,
    PatientInactive,
    UnknownConsentText,
    FutureAcknowledgmentDate,
    MinorConsentRequiresGuardian,
}

/**
 * Business rule: record a consent acknowledgment for an existing, active patient. The consent must
 * reference an *active* consent-text (type, version), can't be acknowledged in the future, and — for
 * a minor — must be acknowledged by the guardian. Captures who acknowledged, when, and which version
 * was shown; attributed to the recording dentist and audited.
 */
class RecordConsentUseCase(
    private val patients: PatientRepository,
    private val consentTexts: ConsentTextRepository,
    private val consents: ConsentRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val zone: ZoneId = ZoneId.of("Asia/Manila"),
) {
    suspend operator fun invoke(patientId: UUID, input: NewConsent, actingUserId: UUID): RecordConsentResult {
        val patient = patients.findById(patientId)
            ?: return RecordConsentResult.Rejected(RecordConsentError.PatientNotFound)
        if (!patient.active) return RecordConsentResult.Rejected(RecordConsentError.PatientInactive)
        val text = consentTexts.find(input.type, input.textVersion)
        if (text == null || !text.active) {
            return RecordConsentResult.Rejected(RecordConsentError.UnknownConsentText)
        }

        val now = clock.now()
        if (input.acknowledgedAt != null && input.acknowledgedAt.isAfter(now)) {
            return RecordConsentResult.Rejected(RecordConsentError.FutureAcknowledgmentDate)
        }
        val isMinor = patient.dateOfBirth?.let { Period.between(it, LocalDate.ofInstant(now, zone)).years < 18 } ?: false
        if (isMinor && input.acknowledgedByRole != AcknowledgedByRole.GUARDIAN) {
            return RecordConsentResult.Rejected(RecordConsentError.MinorConsentRequiresGuardian)
        }

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

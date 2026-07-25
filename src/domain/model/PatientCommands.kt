package com.pms.dental.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Framework-free input value objects for the write use cases — the raw data a caller supplies
 * before the use case mints ids, stamps timestamps, and attributes the acting user. Kept
 * separate from the persisted entities (which carry ids/audit fields) and from the wire DTOs.
 */

/** Demographics for registering or updating a patient (no id / audit / attribution fields). */
data class PatientDemographics(
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val suffix: String?,
    val nickname: String?,
    val dateOfBirth: LocalDate?,
    val sex: Sex,
    val religion: String?,
    val nationality: String?,
    val civilStatus: String?,
    val occupation: String?,
    val address: String?,
    val mobileNumber: String?,
    val homeNumber: String?,
    val officeNumber: String?,
    val email: String?,
    val guardianName: String?,
    val guardianRelationship: String?,
    val guardianOccupation: String?,
    val guardianContact: String?,
    val emergencyContactName: String?,
    val emergencyContactRelationship: String?,
    val emergencyContactNumber: String?,
    val isSenior: Boolean,
    val isPwd: Boolean,
    val scPwdIdNumber: String?,
    val tin: String?,
    val dentalInsurance: String?,
    val insuranceEffectiveDate: LocalDate?,
    val referralSource: String?,
    val isLegacy: Boolean,
    val legacySummary: String?,
    /** Only honored for legacy backfill; non-legacy registrations always use the clock. */
    val registeredAt: Instant?,
)

/** A new allergy to attach to a patient. */
data class NewAllergy(
    val substance: String,
    val severity: AllergySeverity?,
    val note: String?,
)

/** A new/updated answer to an intake question; exactly one value field should be set. */
data class NewAnswer(
    val questionId: UUID,
    val answerBoolean: Boolean?,
    val answerText: String?,
    val answerDate: LocalDate?,
)

/** A consent acknowledgment to record. [acknowledgedAt] defaults to now when null. */
data class NewConsent(
    val type: ConsentType,
    val textVersion: String,
    val acknowledgedByRole: AcknowledgedByRole,
    val acknowledgedByName: String?,
    val acknowledgedAt: Instant?,
)

/** The full intake captured at registration, submitted as one atomic command. */
data class PatientRegistration(
    val demographics: PatientDemographics,
    val allergies: List<NewAllergy>,
    val answers: List<NewAnswer>,
    val consents: List<NewConsent>,
)

package com.pms.dental.patient

import kotlinx.serialization.Serializable

/**
 * Wire DTOs for the patient/intake API. Temporal fields are ISO-8601 **strings** (`dateOfBirth`
 * "1990-05-01"; timestamps as ISO instants) and enums are their name strings — matching the
 * "stringify at the boundary" convention established by the auth slice.
 */

@Serializable
data class PatientProfileDto(
    val lastName: String,
    val firstName: String,
    val middleName: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val dateOfBirth: String? = null,
    val sex: String,
    val religion: String? = null,
    val nationality: String? = null,
    val civilStatus: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val mobileNumber: String? = null,
    val homeNumber: String? = null,
    val officeNumber: String? = null,
    val email: String? = null,
    val guardianName: String? = null,
    val guardianRelationship: String? = null,
    val guardianOccupation: String? = null,
    val guardianContact: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactRelationship: String? = null,
    val emergencyContactNumber: String? = null,
    val isSenior: Boolean = false,
    val isPwd: Boolean = false,
    val scPwdIdNumber: String? = null,
    val tin: String? = null,
    val dentalInsurance: String? = null,
    val insuranceEffectiveDate: String? = null,
    val referralSource: String? = null,
    val isLegacy: Boolean = false,
    val legacySummary: String? = null,
    /** Only honored for legacy backfill; non-legacy registrations always use server time. */
    val registeredAt: String? = null,
)

@Serializable
data class AllergyInput(
    val substance: String,
    val severity: String? = null,
    val note: String? = null,
)

@Serializable
data class IntakeAnswerInput(
    val questionId: String,
    val answerBoolean: Boolean? = null,
    val answerText: String? = null,
    val answerDate: String? = null,
)

@Serializable
data class ConsentInput(
    val type: String,
    val textVersion: String,
    val acknowledgedByRole: String,
    val acknowledgedByName: String? = null,
    val acknowledgedAt: String? = null,
)

@Serializable
data class RegisterPatientRequest(
    val profile: PatientProfileDto,
    val allergies: List<AllergyInput> = emptyList(),
    val answers: List<IntakeAnswerInput> = emptyList(),
    val consents: List<ConsentInput> = emptyList(),
)

@Serializable
data class UpsertAnswersRequest(val answers: List<IntakeAnswerInput> = emptyList())

@Serializable
data class AllergyResponse(
    val id: String,
    val substance: String,
    val severity: String? = null,
    val note: String? = null,
    val active: Boolean,
)

@Serializable
data class IntakeAnswerResponse(
    val id: String,
    val questionId: String,
    val answerBoolean: Boolean? = null,
    val answerText: String? = null,
    val answerDate: String? = null,
)

@Serializable
data class ConsentResponse(
    val id: String,
    val type: String,
    val textVersion: String,
    val acknowledgedByRole: String,
    val acknowledgedByName: String? = null,
    val acknowledgedAt: String,
)

@Serializable
data class PatientResponse(
    val id: String,
    val lastName: String,
    val firstName: String,
    val middleName: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    val dateOfBirth: String? = null,
    val sex: String,
    val religion: String? = null,
    val nationality: String? = null,
    val civilStatus: String? = null,
    val occupation: String? = null,
    val address: String? = null,
    val mobileNumber: String? = null,
    val homeNumber: String? = null,
    val officeNumber: String? = null,
    val email: String? = null,
    val guardianName: String? = null,
    val guardianRelationship: String? = null,
    val guardianOccupation: String? = null,
    val guardianContact: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactRelationship: String? = null,
    val emergencyContactNumber: String? = null,
    val isSenior: Boolean,
    val isPwd: Boolean,
    val scPwdIdNumber: String? = null,
    val tin: String? = null,
    val dentalInsurance: String? = null,
    val insuranceEffectiveDate: String? = null,
    val referralSource: String? = null,
    val isLegacy: Boolean,
    val legacySummary: String? = null,
    val registeredAt: String,
    val active: Boolean,
    val createdAt: String,
    val updatedAt: String? = null,
)

@Serializable
data class PatientSummaryResponse(
    val id: String,
    val lastName: String,
    val firstName: String,
    val middleName: String? = null,
    val dateOfBirth: String? = null,
    val mobileNumber: String? = null,
    val isSenior: Boolean,
    val isPwd: Boolean,
    val isLegacy: Boolean,
    val active: Boolean,
    val registeredAt: String,
)

@Serializable
data class PatientDetailsResponse(
    val patient: PatientResponse,
    val allergies: List<AllergyResponse>,
    val answers: List<IntakeAnswerResponse>,
    val consents: List<ConsentResponse>,
)

@Serializable
data class PatientPageResponse(
    val patients: List<PatientSummaryResponse>,
    val total: Long,
    val page: Int,
    val limit: Int,
)

@Serializable
data class IntakeQuestionResponse(
    val id: String,
    val section: String,
    val code: String,
    val prompt: String,
    val answerType: String,
    val choices: String? = null,
    val displayOrder: Int,
)

@Serializable
data class ConsentTextResponse(
    val id: String,
    val type: String,
    val version: String,
    val title: String,
    val body: String,
)

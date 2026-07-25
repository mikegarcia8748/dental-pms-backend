package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientDetails
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.model.PatientRegistration
import com.pms.dental.domain.repository.ConsentTextRepository
import com.pms.dental.domain.repository.IntakeQuestionRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

/** Outcome of a registration attempt. Failures are data so the route can map them to HTTP status. */
sealed interface RegisterPatientResult {
    data class Success(val details: PatientDetails) : RegisterPatientResult
    data class Rejected(val error: RegisterPatientError) : RegisterPatientResult
}

enum class RegisterPatientError {
    /** DOB present and age < 18, but guardian name/contact were not supplied. */
    MinorRequiresGuardian,

    /** A non-legacy registration did not include a data-privacy (RA 10173) consent. */
    MissingDataPrivacyConsent,

    /** An answer references a question that does not exist or is inactive. */
    UnknownQuestion,

    /** An answer's value does not match its question's answer type. */
    AnswerTypeMismatch,

    /** A consent references a (type, version) that has no matching consent text. */
    UnknownConsentText,

    /** A legacy registration date was in the future. */
    FutureRegistrationDate,
}

/**
 * Business rule: register a patient with their full intake — allergies, medical/dental answers,
 * and consents — in one atomic write, attributed to the acting dentist and audited. Enforces the
 * clinical/legal preconditions (guardian for minors, data-privacy consent, well-formed answer and
 * consent references, no future backdating) before persisting anything.
 */
class RegisterPatientUseCase(
    private val patients: PatientRepository,
    private val questions: IntakeQuestionRepository,
    private val consentTexts: ConsentTextRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val zone: ZoneId = ZoneId.of("Asia/Manila"),
) {
    suspend operator fun invoke(command: PatientRegistration, actingUserId: UUID): RegisterPatientResult {
        val now = clock.now()
        val d = command.demographics

        // Legacy records may carry their real (past) join date; everyone else joins "now".
        val registeredAt = if (d.isLegacy) (d.registeredAt ?: now) else now
        if (d.isLegacy && registeredAt.isAfter(now)) {
            return RegisterPatientResult.Rejected(RegisterPatientError.FutureRegistrationDate)
        }

        // A minor needs a guardian on record.
        d.dateOfBirth?.let { dob ->
            val age = Period.between(dob, LocalDate.ofInstant(now, zone)).years
            if (age < 18 && (d.guardianName.isNullOrBlank() || d.guardianContact.isNullOrBlank())) {
                return RegisterPatientResult.Rejected(RegisterPatientError.MinorRequiresGuardian)
            }
        }

        // The data-privacy basis is mandatory for a fresh (non-legacy) registration.
        if (!d.isLegacy && command.consents.none { it.type == ConsentType.DATA_PRIVACY }) {
            return RegisterPatientResult.Rejected(RegisterPatientError.MissingDataPrivacyConsent)
        }

        // Every answered question must exist, be active, and be answered in its own type.
        val known = questions.findByIds(command.answers.map { it.questionId }.toSet()).associateBy { it.id }
        for (answer in command.answers) {
            val question = known[answer.questionId]
            if (question == null || !question.active) {
                return RegisterPatientResult.Rejected(RegisterPatientError.UnknownQuestion)
            }
            if (!answerMatchesType(question.answerType, answer)) {
                return RegisterPatientResult.Rejected(RegisterPatientError.AnswerTypeMismatch)
            }
        }

        // Every consent must reference a real consent-text version.
        for (consent in command.consents) {
            if (consentTexts.find(consent.type, consent.textVersion) == null) {
                return RegisterPatientResult.Rejected(RegisterPatientError.UnknownConsentText)
            }
        }

        val patientId = idGenerator.newId()
        val patient = Patient(
            id = patientId,
            lastName = d.lastName.trim(),
            firstName = d.firstName.trim(),
            middleName = d.middleName?.trim(),
            suffix = d.suffix?.trim(),
            nickname = d.nickname?.trim(),
            dateOfBirth = d.dateOfBirth,
            sex = d.sex,
            religion = d.religion?.trim(),
            nationality = d.nationality?.trim(),
            civilStatus = d.civilStatus?.trim(),
            occupation = d.occupation?.trim(),
            address = d.address?.trim(),
            mobileNumber = d.mobileNumber?.trim(),
            homeNumber = d.homeNumber?.trim(),
            officeNumber = d.officeNumber?.trim(),
            email = d.email?.trim()?.lowercase(),
            guardianName = d.guardianName?.trim(),
            guardianRelationship = d.guardianRelationship?.trim(),
            guardianOccupation = d.guardianOccupation?.trim(),
            guardianContact = d.guardianContact?.trim(),
            emergencyContactName = d.emergencyContactName?.trim(),
            emergencyContactRelationship = d.emergencyContactRelationship?.trim(),
            emergencyContactNumber = d.emergencyContactNumber?.trim(),
            isSenior = d.isSenior,
            isPwd = d.isPwd,
            scPwdIdNumber = d.scPwdIdNumber?.trim(),
            tin = d.tin?.trim(),
            dentalInsurance = d.dentalInsurance?.trim(),
            insuranceEffectiveDate = d.insuranceEffectiveDate,
            referralSource = d.referralSource?.trim(),
            isLegacy = d.isLegacy,
            legacySummary = d.legacySummary?.trim(),
            registeredAt = registeredAt,
            active = true,
            createdBy = actingUserId,
            createdAt = now,
            updatedBy = null,
            updatedAt = null,
        )

        val allergies = command.allergies.map { input ->
            Allergy(
                id = idGenerator.newId(),
                patientId = patientId,
                substance = input.substance.trim(),
                severity = input.severity,
                note = input.note?.trim(),
                active = true,
                recordedBy = actingUserId,
                recordedAt = now,
            )
        }

        val answers = command.answers.map { input ->
            PatientIntakeAnswer(
                id = idGenerator.newId(),
                patientId = patientId,
                questionId = input.questionId,
                answerBoolean = input.answerBoolean,
                answerText = input.answerText?.trim(),
                answerDate = input.answerDate,
                recordedBy = actingUserId,
                recordedAt = now,
            )
        }

        val consents = command.consents.map { input ->
            Consent(
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
        }

        val audit = buildList {
            add(auditEntry(actingUserId, AuditAction.CREATE, "patient", patientId, now))
            allergies.forEach { add(auditEntry(actingUserId, AuditAction.CREATE, "allergy", it.id, now)) }
            answers.forEach { add(auditEntry(actingUserId, AuditAction.CREATE, "intake_answer", it.id, now)) }
            consents.forEach { add(auditEntry(actingUserId, AuditAction.CREATE, "consent", it.id, now)) }
        }

        patients.insertRegistration(patient, allergies, answers, consents, audit)
        return RegisterPatientResult.Success(PatientDetails(patient, allergies, answers, consents))
    }

    private fun auditEntry(userId: UUID, action: AuditAction, entity: String, entityId: UUID, at: java.time.Instant) =
        AuditEntry(idGenerator.newId(), userId, action, entity, entityId, at)
}

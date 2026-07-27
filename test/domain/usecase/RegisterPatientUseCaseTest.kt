package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.AllergySeverity
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.NewAllergy
import com.pms.dental.domain.model.NewAnswer
import com.pms.dental.domain.model.NewConsent
import com.pms.dental.domain.model.PatientRegistration
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeConsentTextRepository
import com.pms.dental.support.FakeIntakeQuestionRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.consentText
import com.pms.dental.support.demographics
import com.pms.dental.support.question
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private class Fixture {
    val patients = FakePatientRepository()
    val questions = FakeIntakeQuestionRepository()
    val consentTexts = FakeConsentTextRepository()
    val actingUser: UUID = UUID.randomUUID()
    val useCase = RegisterPatientUseCase(patients, questions, consentTexts, Clock { FIXED_NOW }, SequentialIds())

    fun dataPrivacyConsent() = NewConsent(ConsentType.DATA_PRIVACY, "RA10173-v1", AcknowledgedByRole.PATIENT, null, null)
    fun dataPrivacyConsentByGuardian() =
        NewConsent(ConsentType.DATA_PRIVACY, "RA10173-v1", AcknowledgedByRole.GUARDIAN, "Maria Dela Cruz", null)
}

class RegisterPatientUseCaseTest : FunSpec({

    test("patient registration - minor without guardian details - rejected with MinorRequiresGuardian") {
        val f = Fixture()
        val command = PatientRegistration(
            demographics = demographics(dateOfBirth = LocalDate.of(2015, 1, 1)), // ~11 years old
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(f.dataPrivacyConsent()),
        )

        val result = f.useCase(command, f.actingUser)

        result.shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.MinorRequiresGuardian
        f.patients.patients.values.shouldBeEmpty()
    }

    test("patient registration - minor with guardian details - is accepted") {
        val f = Fixture()
        f.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        val command = PatientRegistration(
            demographics = demographics(
                dateOfBirth = LocalDate.of(2015, 1, 1),
                guardianName = "Maria Dela Cruz",
                guardianContact = "09170000001",
            ),
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(f.dataPrivacyConsentByGuardian()),
        )

        f.useCase(command, f.actingUser).shouldBeInstanceOf<RegisterPatientResult.Success>()
    }

    test("patient registration - minor's consent acknowledged by the patient - rejected with MinorConsentRequiresGuardian") {
        val f = Fixture()
        f.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        val command = PatientRegistration(
            demographics = demographics(
                dateOfBirth = LocalDate.of(2015, 1, 1),
                guardianName = "Maria Dela Cruz",
                guardianContact = "09170000001",
            ),
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(f.dataPrivacyConsent()), // acknowledgedByRole = PATIENT
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.MinorConsentRequiresGuardian
    }

    test("patient registration - date of birth in the future - rejected with FutureDateOfBirth") {
        val f = Fixture()
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true, dateOfBirth = LocalDate.of(2999, 1, 1)),
            allergies = emptyList(),
            answers = emptyList(),
            consents = emptyList(),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.FutureDateOfBirth
    }

    test("patient registration - choice answer outside the allowed set - rejected with AnswerNotInChoices") {
        val f = Fixture()
        val choiceQ = question("blood_type", IntakeAnswerType.CHOICE).copy(choices = """["A","B","O","AB"]""")
        f.questions.seed(choiceQ)
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true),
            allergies = emptyList(),
            answers = listOf(NewAnswer(choiceQ.id, answerBoolean = null, answerText = "Z", answerDate = null)),
            consents = emptyList(),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.AnswerNotInChoices
    }

    test("patient registration - consent references an inactive text version - rejected with UnknownConsentText") {
        val f = Fixture()
        f.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1", active = false))
        val command = PatientRegistration(
            demographics = demographics(),
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(f.dataPrivacyConsent()),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.UnknownConsentText
    }

    test("patient registration - non-legacy without data-privacy consent - rejected with MissingDataPrivacyConsent") {
        val f = Fixture()
        val command = PatientRegistration(demographics(), emptyList(), emptyList(), consents = emptyList())

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.MissingDataPrivacyConsent
    }

    test("patient registration - legacy backfill, sparse fields, past date - succeeds and preserves registeredAt") {
        val f = Fixture()
        val joined = Instant.parse("2015-03-01T00:00:00Z")
        val command = PatientRegistration(
            demographics = demographics(
                dateOfBirth = null,
                mobileNumber = null,
                isLegacy = true,
                legacySummary = "Old paper chart: 2 fillings, 1 extraction (molar).",
                registeredAt = joined,
            ),
            allergies = emptyList(),
            answers = emptyList(),
            consents = emptyList(), // legacy backfill does not require a data-privacy consent
        )

        val result = f.useCase(command, f.actingUser).shouldBeInstanceOf<RegisterPatientResult.Success>()

        result.details.patient.registeredAt shouldBe joined
        result.details.patient.isLegacy shouldBe true
        result.details.patient.createdAt shouldBe FIXED_NOW // system time is still "now"
        f.patients.patients.values shouldHaveSize 1
    }

    test("patient registration - legacy registration date in the future - rejected with FutureRegistrationDate") {
        val f = Fixture()
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true, registeredAt = FIXED_NOW.plusSeconds(86_400)),
            allergies = emptyList(),
            answers = emptyList(),
            consents = emptyList(),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.FutureRegistrationDate
    }

    test("patient registration - answer references inactive question - rejected with UnknownQuestion") {
        val f = Fixture()
        val inactive = question("cond_diabetes", IntakeAnswerType.BOOLEAN, active = false)
        f.questions.seed(inactive)
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true), // legacy → skip data-privacy requirement
            allergies = emptyList(),
            answers = listOf(NewAnswer(inactive.id, answerBoolean = true, answerText = null, answerDate = null)),
            consents = emptyList(),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.UnknownQuestion
    }

    test("patient registration - boolean value on a TEXT question - rejected with AnswerTypeMismatch") {
        val f = Fixture()
        val textQ = question("blood_type", IntakeAnswerType.TEXT)
        f.questions.seed(textQ)
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true),
            allergies = emptyList(),
            answers = listOf(NewAnswer(textQ.id, answerBoolean = true, answerText = null, answerDate = null)),
            consents = emptyList(),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.AnswerTypeMismatch
    }

    test("patient registration - consent references unknown text version - rejected with UnknownConsentText") {
        val f = Fixture()
        // No consent_text seeded, so the referenced (TREATMENT, missing) cannot resolve.
        val command = PatientRegistration(
            demographics = demographics(isLegacy = true),
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(NewConsent(ConsentType.TREATMENT, "missing", AcknowledgedByRole.PATIENT, null, null)),
        )

        f.useCase(command, f.actingUser)
            .shouldBeInstanceOf<RegisterPatientResult.Rejected>().error shouldBe RegisterPatientError.UnknownConsentText
    }

    test("patient registration - valid full intake - persists patient, allergies, answers, consents and writes CREATE audit") {
        val f = Fixture()
        val boolQ = question("good_health", IntakeAnswerType.BOOLEAN)
        f.questions.seed(boolQ)
        f.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        f.consentTexts.seed(consentText(ConsentType.TREATMENT, "PDA-2010"))
        val command = PatientRegistration(
            demographics = demographics(),
            allergies = listOf(NewAllergy("Penicillin", AllergySeverity.SEVERE, note = null)),
            answers = listOf(NewAnswer(boolQ.id, answerBoolean = true, answerText = null, answerDate = null)),
            consents = listOf(
                NewConsent(ConsentType.DATA_PRIVACY, "RA10173-v1", AcknowledgedByRole.PATIENT, null, null),
                NewConsent(ConsentType.TREATMENT, "PDA-2010", AcknowledgedByRole.PATIENT, null, null),
            ),
        )

        val result = f.useCase(command, f.actingUser).shouldBeInstanceOf<RegisterPatientResult.Success>()

        result.details.allergies shouldHaveSize 1
        result.details.answers shouldHaveSize 1
        result.details.consents shouldHaveSize 2

        val registration = f.patients.registrations.single()
        // one CREATE audit per entity: patient + 1 allergy + 1 answer + 2 consents = 5
        registration.audit shouldHaveSize 5
        registration.audit.forEach { it.action shouldBe AuditAction.CREATE }
        registration.audit.forEach { it.userId shouldBe f.actingUser }
        registration.audit.count { it.entity == "patient" && it.entityId == result.details.patient.id } shouldBe 1
    }

    test("patient registration - repository throws mid-insert - nothing is persisted") {
        val f = Fixture()
        f.patients.failOnInsert = true
        f.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        val command = PatientRegistration(
            demographics = demographics(),
            allergies = emptyList(),
            answers = emptyList(),
            consents = listOf(f.dataPrivacyConsent()),
        )

        shouldThrow<IllegalStateException> { f.useCase(command, f.actingUser) }
        f.patients.patients.values.shouldBeEmpty()
    }
})

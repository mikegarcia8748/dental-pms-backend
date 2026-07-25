package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.NewConsent
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakeConsentRepository
import com.pms.dental.support.FakeConsentTextRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.consentText
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID

private class ConsentFixture {
    val patients = FakePatientRepository()
    val consentTexts = FakeConsentTextRepository()
    val consents = FakeConsentRepository()
    val audit = FakeAuditLogRepository()
    val useCase = RecordConsentUseCase(patients, consentTexts, consents, audit, Clock { FIXED_NOW }, SequentialIds())
}

class RecordConsentUseCaseTest : FunSpec({

    test("consent - unknown patient - PatientNotFound") {
        val f = ConsentFixture()

        f.useCase(
            UUID.randomUUID(),
            NewConsent(ConsentType.TREATMENT, "PDA-2010", AcknowledgedByRole.PATIENT, null, null),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<RecordConsentResult.Rejected>().error shouldBe RecordConsentError.PatientNotFound
    }

    test("consent - unknown text version - rejected with UnknownConsentText") {
        val f = ConsentFixture()
        val p = patient()
        f.patients.seed(p)

        f.useCase(
            p.id,
            NewConsent(ConsentType.TREATMENT, "does-not-exist", AcknowledgedByRole.PATIENT, null, null),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<RecordConsentResult.Rejected>().error shouldBe RecordConsentError.UnknownConsentText
    }

    test("consent - valid acknowledgment - persists role, name, version and audits") {
        val f = ConsentFixture()
        val p = patient()
        f.patients.seed(p)
        f.consentTexts.seed(consentText(ConsentType.TREATMENT, "PDA-2010"))

        val success = f.useCase(
            p.id,
            NewConsent(ConsentType.TREATMENT, "PDA-2010", AcknowledgedByRole.GUARDIAN, "Maria Dela Cruz", null),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<RecordConsentResult.Success>()

        success.consent.acknowledgedByRole shouldBe AcknowledgedByRole.GUARDIAN
        success.consent.acknowledgedByName shouldBe "Maria Dela Cruz"
        success.consent.textVersion shouldBe "PDA-2010"
        success.consent.acknowledgedAt shouldBe FIXED_NOW // defaulted to now when not supplied
        f.consents.listByPatient(p.id).single().id shouldBe success.consent.id
        f.audit.entries.single().action shouldBe AuditAction.CREATE
    }
})

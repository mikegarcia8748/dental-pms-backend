package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AllergySeverity
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.NewAllergy
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeAllergyRepository
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID

private fun allergyOf(patientId: UUID, active: Boolean = true) = Allergy(
    id = UUID.randomUUID(),
    patientId = patientId,
    substance = "Latex",
    severity = AllergySeverity.MILD,
    note = null,
    active = active,
    recordedBy = UUID.randomUUID(),
    recordedAt = FIXED_NOW,
)

class AllergyUseCasesTest : FunSpec({

    test("allergy add - unknown patient - PatientNotFound") {
        val patients = FakePatientRepository()
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val useCase = AddAllergyUseCase(patients, allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        useCase(UUID.randomUUID(), NewAllergy("Penicillin", null, null), UUID.randomUUID()) shouldBe
            AddAllergyResult.PatientNotFound
    }

    test("allergy add - valid - persists and audits") {
        val patients = FakePatientRepository()
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient()
        patients.seed(existing)
        val useCase = AddAllergyUseCase(patients, allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        val success = useCase(existing.id, NewAllergy("Penicillin", AllergySeverity.SEVERE, "rash"), UUID.randomUUID())
            .shouldBeInstanceOf<AddAllergyResult.Success>()

        allergies.byId.getValue(success.allergy.id).substance shouldBe "Penicillin"
        audit.entries.single().action shouldBe AuditAction.CREATE
    }

    test("allergy update - allergy belongs to a different patient - NotFound") {
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val other = allergyOf(patientId = UUID.randomUUID())
        allergies.seed(other)
        val useCase = UpdateAllergyUseCase(allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        useCase(UUID.randomUUID(), other.id, NewAllergy("Aspirin", null, null), UUID.randomUUID()) shouldBe
            UpdateAllergyResult.NotFound
    }

    test("allergy update - valid - updates fields and audits") {
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val patientId = UUID.randomUUID()
        val existing = allergyOf(patientId)
        allergies.seed(existing)
        val useCase = UpdateAllergyUseCase(allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        val success = useCase(patientId, existing.id, NewAllergy("Aspirin", AllergySeverity.MODERATE, "hives"), UUID.randomUUID())
            .shouldBeInstanceOf<UpdateAllergyResult.Success>()

        success.allergy.substance shouldBe "Aspirin"
        success.allergy.severity shouldBe AllergySeverity.MODERATE
        audit.entries.single().action shouldBe AuditAction.UPDATE
    }

    test("allergy deactivate - active allergy - becomes inactive") {
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val patientId = UUID.randomUUID()
        val existing = allergyOf(patientId, active = true)
        allergies.seed(existing)
        val useCase = DeactivateAllergyUseCase(allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        useCase(patientId, existing.id, UUID.randomUUID()) shouldBe DeactivateAllergyResult.Success

        allergies.byId.getValue(existing.id).active shouldBe false
        audit.entries.single().action shouldBe AuditAction.DEACTIVATE
    }

    test("allergy deactivate - already inactive - idempotent success without re-audit") {
        val allergies = FakeAllergyRepository()
        val audit = FakeAuditLogRepository()
        val patientId = UUID.randomUUID()
        val existing = allergyOf(patientId, active = false)
        allergies.seed(existing)
        val useCase = DeactivateAllergyUseCase(allergies, audit, Clock { FIXED_NOW }, SequentialIds())

        useCase(patientId, existing.id, UUID.randomUUID()) shouldBe DeactivateAllergyResult.Success

        audit.entries.size shouldBe 0
    }
})

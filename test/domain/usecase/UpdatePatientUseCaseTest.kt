package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.demographics
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class UpdatePatientUseCaseTest : FunSpec({

    fun useCase(patients: FakePatientRepository, audit: FakeAuditLogRepository) =
        UpdatePatientUseCase(patients, audit, Clock { FIXED_NOW }, SequentialIds())

    test("patient update - unknown id - NotFound") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()

        useCase(patients, audit)(UUID.randomUUID(), demographics(), UUID.randomUUID())
            .shouldBeInstanceOf<UpdatePatientResult.Rejected>().error shouldBe UpdatePatientError.NotFound
    }

    test("patient update - valid edit - persists changes and records updated_by") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient()
        patients.seed(existing)
        val actor = UUID.randomUUID()

        val success = useCase(patients, audit)(existing.id, demographics(lastName = "Reyes", firstName = "Ana"), actor)
            .shouldBeInstanceOf<UpdatePatientResult.Success>()

        success.patient.lastName shouldBe "Reyes"
        success.patient.firstName shouldBe "Ana"
        success.patient.updatedBy shouldBe actor
        success.patient.updatedAt shouldBe FIXED_NOW
        patients.patients.getValue(existing.id).lastName shouldBe "Reyes"
        audit.entries.single().action shouldBe AuditAction.UPDATE
    }

    test("patient update - edit drops guardian for a minor - rejected with MinorRequiresGuardian") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient()
        patients.seed(existing)

        useCase(patients, audit)(
            existing.id,
            demographics(dateOfBirth = LocalDate.of(2015, 1, 1), guardianName = null, guardianContact = null),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpdatePatientResult.Rejected>().error shouldBe UpdatePatientError.MinorRequiresGuardian
    }

    test("patient update - deactivated patient - rejected with PatientInactive") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(active = false)
        patients.seed(existing)

        useCase(patients, audit)(existing.id, demographics(), UUID.randomUUID())
            .shouldBeInstanceOf<UpdatePatientResult.Rejected>().error shouldBe UpdatePatientError.PatientInactive
    }

    test("patient update - date of birth in the future - rejected with FutureDateOfBirth") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient()
        patients.seed(existing)

        useCase(patients, audit)(existing.id, demographics(dateOfBirth = LocalDate.of(2999, 1, 1)), UUID.randomUUID())
            .shouldBeInstanceOf<UpdatePatientResult.Rejected>().error shouldBe UpdatePatientError.FutureDateOfBirth
    }

    test("patient update - legacy record corrects its backdated registeredAt") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(registeredAt = FIXED_NOW)
        patients.seed(existing)
        val corrected = Instant.parse("2015-03-01T00:00:00Z")

        val success = useCase(patients, audit)(
            existing.id,
            demographics(isLegacy = true, registeredAt = corrected),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpdatePatientResult.Success>()

        success.patient.registeredAt shouldBe corrected
    }

    test("patient update - legacy registeredAt in the future - rejected with FutureRegistrationDate") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient()
        patients.seed(existing)

        useCase(patients, audit)(
            existing.id,
            demographics(isLegacy = true, registeredAt = FIXED_NOW.plusSeconds(86_400)),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpdatePatientResult.Rejected>().error shouldBe UpdatePatientError.FutureRegistrationDate
    }

    test("patient update - non-legacy edit keeps the original registeredAt") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(registeredAt = FIXED_NOW)
        patients.seed(existing)

        val success = useCase(patients, audit)(
            existing.id,
            demographics(registeredAt = Instant.parse("2015-03-01T00:00:00Z")), // non-legacy → ignored
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpdatePatientResult.Success>()

        success.patient.registeredAt shouldBe FIXED_NOW
    }
})

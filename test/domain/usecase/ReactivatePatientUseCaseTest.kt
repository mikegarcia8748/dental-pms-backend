package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.util.UUID

class ReactivatePatientUseCaseTest : FunSpec({

    fun useCase(patients: FakePatientRepository, audit: FakeAuditLogRepository) =
        ReactivatePatientUseCase(patients, audit, Clock { FIXED_NOW }, SequentialIds())

    test("patient reactivation - unknown id - NotFound") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()

        useCase(patients, audit)(UUID.randomUUID(), UUID.randomUUID()) shouldBe ReactivatePatientResult.NotFound
    }

    test("patient reactivation - inactive patient - becomes active and audits") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(active = false)
        patients.seed(existing)
        val actor = UUID.randomUUID()

        useCase(patients, audit)(existing.id, actor) shouldBe ReactivatePatientResult.Success

        patients.patients.getValue(existing.id).active shouldBe true
        patients.patients.getValue(existing.id).updatedBy shouldBe actor
        audit.entries.single().action shouldBe AuditAction.REACTIVATE
    }

    test("patient reactivation - already active - stays active and does not re-audit (idempotent)") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(active = true)
        patients.seed(existing)

        useCase(patients, audit)(existing.id, UUID.randomUUID()) shouldBe ReactivatePatientResult.Success

        patients.patients.getValue(existing.id).active shouldBe true
        audit.entries.shouldBeEmpty()
    }
})

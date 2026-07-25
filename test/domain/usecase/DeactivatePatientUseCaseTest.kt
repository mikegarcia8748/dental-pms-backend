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

class DeactivatePatientUseCaseTest : FunSpec({

    fun useCase(patients: FakePatientRepository, audit: FakeAuditLogRepository) =
        DeactivatePatientUseCase(patients, audit, Clock { FIXED_NOW }, SequentialIds())

    test("patient deactivation - unknown id - NotFound") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()

        useCase(patients, audit)(UUID.randomUUID(), UUID.randomUUID()) shouldBe DeactivatePatientResult.NotFound
    }

    test("patient deactivation - active patient - becomes inactive and audits") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(active = true)
        patients.seed(existing)

        useCase(patients, audit)(existing.id, UUID.randomUUID()) shouldBe DeactivatePatientResult.Success

        patients.patients.getValue(existing.id).active shouldBe false
        audit.entries.single().action shouldBe AuditAction.DEACTIVATE
    }

    test("patient deactivation - already inactive - stays inactive and does not re-audit (idempotent)") {
        val patients = FakePatientRepository()
        val audit = FakeAuditLogRepository()
        val existing = patient(active = false)
        patients.seed(existing)

        useCase(patients, audit)(existing.id, UUID.randomUUID()) shouldBe DeactivatePatientResult.Success

        patients.patients.getValue(existing.id).active shouldBe false
        audit.entries.shouldBeEmpty()
    }
})

package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.support.FakeAllergyRepository
import com.pms.dental.support.FakeConsentRepository
import com.pms.dental.support.FakePatientIntakeAnswerRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.util.UUID

class GetPatientDetailsUseCaseTest : FunSpec({

    test("patient details - unknown id - NotFound") {
        val useCase = GetPatientDetailsUseCase(
            FakePatientRepository(),
            FakeAllergyRepository(),
            FakePatientIntakeAnswerRepository(),
            FakeConsentRepository(),
        )

        useCase(UUID.randomUUID()) shouldBe PatientDetailsResult.NotFound
    }

    test("patient details - existing - returns nested allergies, answers and consents") {
        val patients = FakePatientRepository()
        val allergies = FakeAllergyRepository()
        val answers = FakePatientIntakeAnswerRepository()
        val consents = FakeConsentRepository()
        val p = patient()
        patients.seed(p)
        allergies.seed(Allergy(UUID.randomUUID(), p.id, "Latex", null, null, true, UUID.randomUUID(), FIXED_NOW))
        answers.upsertAll(listOf(PatientIntakeAnswer(UUID.randomUUID(), p.id, UUID.randomUUID(), true, null, null, UUID.randomUUID(), FIXED_NOW)))
        consents.insert(Consent(UUID.randomUUID(), p.id, ConsentType.TREATMENT, "PDA-2010", AcknowledgedByRole.PATIENT, null, FIXED_NOW, UUID.randomUUID(), FIXED_NOW))

        val useCase = GetPatientDetailsUseCase(patients, allergies, answers, consents)
        val found = useCase(p.id).shouldBeInstanceOf<PatientDetailsResult.Found>()

        found.details.patient.id shouldBe p.id
        found.details.allergies shouldHaveSize 1
        found.details.answers shouldHaveSize 1
        found.details.consents shouldHaveSize 1
    }
})

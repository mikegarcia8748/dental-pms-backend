package com.pms.dental.domain.usecase

import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.patient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.Instant

class ListPatientsUseCaseTest : FunSpec({

    test("patient search - blank query - returns active patients page with total") {
        val patients = FakePatientRepository()
        patients.seed(patient(lastName = "Reyes"))
        patients.seed(patient(lastName = "Santos"))
        val useCase = ListPatientsUseCase(patients)

        val page = useCase(query = "", page = 1, limit = 20, includeInactive = false)

        page.total shouldBe 2
        page.patients shouldHaveSize 2
    }

    test("patient search - query matches last or first name - returns matches") {
        val patients = FakePatientRepository()
        patients.seed(patient(lastName = "Reyes"))
        patients.seed(patient(lastName = "Santos"))
        val useCase = ListPatientsUseCase(patients)

        val page = useCase(query = "rey", page = 1, limit = 20, includeInactive = false)

        page.patients.single().lastName shouldBe "Reyes"
    }

    test("patient search - includeInactive false - excludes deactivated") {
        val patients = FakePatientRepository()
        patients.seed(patient(lastName = "Reyes", active = true))
        patients.seed(patient(lastName = "Santos", active = false))
        val useCase = ListPatientsUseCase(patients)

        val page = useCase(query = "", page = 1, limit = 20, includeInactive = false)

        page.total shouldBe 1
        page.patients.single().lastName shouldBe "Reyes"
    }

    test("patient search - paging - respects limit and offset") {
        val patients = FakePatientRepository()
        patients.seed(patient(lastName = "A", registeredAt = Instant.parse("2026-03-01T00:00:00Z")))
        patients.seed(patient(lastName = "B", registeredAt = Instant.parse("2026-02-01T00:00:00Z")))
        patients.seed(patient(lastName = "C", registeredAt = Instant.parse("2026-01-01T00:00:00Z")))
        val useCase = ListPatientsUseCase(patients)

        val first = useCase(query = "", page = 1, limit = 2, includeInactive = false)
        val second = useCase(query = "", page = 2, limit = 2, includeInactive = false)

        first.patients shouldHaveSize 2
        first.total shouldBe 3
        second.patients shouldHaveSize 1
        // newest-first ordering: page 2 holds the oldest record
        second.patients.single().lastName shouldBe "C"
    }
})

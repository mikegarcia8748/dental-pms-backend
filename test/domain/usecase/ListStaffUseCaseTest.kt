package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.support.FakeAppUserRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class ListStaffUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = FakeAppUserRepository()
    val list = ListStaffUseCase(users)

    val activeStaff = AppUser(UUID.randomUUID(), "active@clinic.test", "Active", Role.DENTIST, true, null, "u1", AuthSource.FIREBASE)
    val inactiveStaff = AppUser(UUID.randomUUID(), "inactive@clinic.test", "Inactive", Role.DENTIST, false, null, "u2", AuthSource.FIREBASE)
    users.seed(activeStaff)
    users.seed(inactiveStaff)

    given("listing staff") {

        `when`("no filter is given") {
            then("it returns everyone") {
                list().map { it.email }.toSet() shouldBe setOf("active@clinic.test", "inactive@clinic.test")
            }
        }

        `when`("filtering to active only") {
            then("it returns just the active accounts") {
                list(activeOnly = true).map { it.email } shouldBe listOf("active@clinic.test")
            }
        }

        `when`("filtering to inactive only") {
            then("it returns just the inactive accounts") {
                list(activeOnly = false).map { it.email } shouldBe listOf("inactive@clinic.test")
            }
        }
    }
})

package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import java.util.UUID

class OffboardStaffUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val offboard = OffboardStaffUseCase(users)

    val id = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd")

    given("offboarding a staff member") {

        `when`("the account has a Firebase identity") {
            then("deactivating in Neon is the whole operation — that is what blocks the next request") {
                val user = AppUser(id, "staff@clinic.test", "Dr. New", Role.DENTIST, true, null, "fb-uid", AuthSource.FIREBASE)
                coEvery { users.findById(id) } returns user
                coEvery { users.setActive(id, false) } just Runs

                offboard(id) shouldBe OffboardStaffResult.Offboarded

                coVerify(exactly = 1) { users.setActive(id, false) }
            }
        }

        `when`("the account is a LOCAL break-glass account") {
            then("it deactivates the same way") {
                val user = AppUser(id, "admin@clinic.test", "Admin", Role.SYSADMIN, true, "hash", null, AuthSource.LOCAL)
                coEvery { users.findById(id) } returns user
                coEvery { users.setActive(id, false) } just Runs

                offboard(id) shouldBe OffboardStaffResult.Offboarded

                coVerify(exactly = 1) { users.setActive(id, false) }
            }
        }

        `when`("the staff member does not exist") {
            then("it returns NotFound and changes nothing") {
                coEvery { users.findById(id) } returns null

                offboard(id) shouldBe OffboardStaffResult.NotFound

                coVerify(exactly = 0) { users.setActive(any(), any()) }
            }
        }
    }
})

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

class ReactivateStaffUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val reactivate = ReactivateStaffUseCase(users)

    val id = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee")

    given("reactivating a staff member") {

        `when`("the account had already claimed its invite") {
            then("restoring Neon access is enough — the bound Firebase identity still maps here") {
                val user = AppUser(id, "staff@clinic.test", "Dr. New", Role.DENTIST, false, null, "fb-uid", AuthSource.FIREBASE)
                coEvery { users.findById(id) } returns user
                coEvery { users.setActive(id, true) } just Runs

                reactivate(id) shouldBe ReactivateStaffResult.Reactivated

                coVerify(exactly = 1) { users.setActive(id, true) }
            }
        }

        `when`("the staff member does not exist") {
            then("it returns NotFound and changes nothing") {
                coEvery { users.findById(id) } returns null

                reactivate(id) shouldBe ReactivateStaffResult.NotFound

                coVerify(exactly = 0) { users.setActive(any(), any()) }
            }
        }
    }
})

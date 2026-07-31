package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.service.IdGenerator
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.util.UUID

class ProvisionStaffUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val idGenerator = mockk<IdGenerator>()
    val provision = ProvisionStaffUseCase(users, idGenerator)

    val newId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc")

    given("inviting a staff member") {

        `when`("the email is not yet in use") {
            then("it writes an unclaimed FIREBASE row — no Firebase call, no password") {
                coEvery { users.findByEmail("staff@clinic.test") } returns null
                every { idGenerator.newId() } returns newId
                coEvery { users.insert(any()) } just Runs

                val result = provision("  Staff@Clinic.TEST ", "Dr. New", Role.DENTIST)

                result.shouldBeInstanceOf<ProvisionStaffResult.Provisioned>()
                coVerify(exactly = 1) {
                    users.insert(
                        match {
                            it.id == newId && it.email == "staff@clinic.test" && it.role == Role.DENTIST &&
                                it.active && it.passwordHash == null &&
                                // Unclaimed: the first Google sign-in with this email binds the UID.
                                it.firebaseUid == null && it.authSource == AuthSource.FIREBASE
                        },
                    )
                }
            }
        }

        `when`("the display name arrives padded with whitespace") {
            then("it is trimmed before it is stored") {
                coEvery { users.findByEmail("staff@clinic.test") } returns null
                every { idGenerator.newId() } returns newId
                coEvery { users.insert(any()) } just Runs

                provision("staff@clinic.test", "  Dr. New  ", Role.DENTIST)
                    .shouldBeInstanceOf<ProvisionStaffResult.Provisioned>()

                coVerify(exactly = 1) { users.insert(match { it.displayName == "Dr. New" }) }
            }
        }

        `when`("a Firebase account already exists for the email") {
            then("it is a no-op returning AlreadyProvisioned") {
                val existing = AppUser(
                    UUID.randomUUID(), "staff@clinic.test", "Dr. New", Role.DENTIST, true, null, "fb-uid", AuthSource.FIREBASE,
                )
                coEvery { users.findByEmail("staff@clinic.test") } returns existing

                provision("staff@clinic.test", "Dr. New", Role.DENTIST) shouldBe ProvisionStaffResult.AlreadyProvisioned

                coVerify(exactly = 0) { users.insert(any()) }
            }
        }

        `when`("an invite for the email exists but is still unclaimed") {
            then("re-inviting is still AlreadyProvisioned — it must not mint a second row") {
                val pending = AppUser(
                    UUID.randomUUID(), "staff@clinic.test", "Dr. New", Role.DENTIST, true, null, null, AuthSource.FIREBASE,
                )
                coEvery { users.findByEmail("staff@clinic.test") } returns pending

                provision("staff@clinic.test", "Dr. New", Role.DENTIST) shouldBe ProvisionStaffResult.AlreadyProvisioned

                coVerify(exactly = 0) { users.insert(any()) }
            }
        }

        `when`("a LOCAL break-glass account already uses the email") {
            then("it is rejected with LocalAccountExists and the account is never converted") {
                val local = AppUser(
                    UUID.randomUUID(), "admin@clinic.test", "Admin", Role.SYSADMIN, true, "hash", null, AuthSource.LOCAL,
                )
                coEvery { users.findByEmail("admin@clinic.test") } returns local

                provision("admin@clinic.test", "Admin", Role.SYSADMIN) shouldBe ProvisionStaffResult.LocalAccountExists

                coVerify(exactly = 0) { users.insert(any()) }
            }
        }
    }
})

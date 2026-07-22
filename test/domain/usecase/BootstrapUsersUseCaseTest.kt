package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.service.IdGenerator
import com.pms.dental.domain.service.PasswordHasher
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.util.UUID

class BootstrapUsersUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val passwordHasher = mockk<PasswordHasher>()
    val idGenerator = mockk<IdGenerator>()
    val bootstrap = BootstrapUsersUseCase(users, passwordHasher, idGenerator)

    val sysId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    val dentistId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")

    val accounts = listOf(
        BootstrapAccount("  SysAdmin@Clinic.TEST ", "sys-pw", "System Admin", Role.SYSADMIN),
        BootstrapAccount("dentist@clinic.test", "dentist-pw", "Dr. Molar", Role.DENTIST),
    )

    given("first-run bootstrap") {

        `when`("the user table already has accounts") {
            then("it skips and inserts nothing") {
                coEvery { users.countAll() } returns 1

                bootstrap(accounts) shouldBe BootstrapResult.Skipped

                coVerify(exactly = 0) { users.insert(any()) }
            }
        }

        `when`("the table is empty but nothing is configured to seed") {
            then("it skips") {
                coEvery { users.countAll() } returns 0

                bootstrap(emptyList()) shouldBe BootstrapResult.Skipped

                coVerify(exactly = 0) { users.insert(any()) }
            }
        }

        `when`("the table is empty and accounts are configured") {
            then("it seeds every account, active, with a hashed password and normalized email") {
                coEvery { users.countAll() } returns 0
                every { idGenerator.newId() } returnsMany listOf(sysId, dentistId)
                every { passwordHasher.hash("sys-pw") } returns "sys-hash"
                every { passwordHasher.hash("dentist-pw") } returns "dentist-hash"
                coEvery { users.insert(any()) } just Runs

                bootstrap(accounts) shouldBe BootstrapResult.Seeded(2)

                coVerify(exactly = 1) {
                    users.insert(match {
                        it.id == sysId && it.email == "sysadmin@clinic.test" &&
                            it.role == Role.SYSADMIN && it.active && it.passwordHash == "sys-hash"
                    })
                }
                coVerify(exactly = 1) {
                    users.insert(match {
                        it.id == dentistId && it.email == "dentist@clinic.test" &&
                            it.role == Role.DENTIST && it.active && it.passwordHash == "dentist-hash"
                    })
                }
            }
        }
    }
})

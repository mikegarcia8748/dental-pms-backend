package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.AuthTokens
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.GeneratedRefreshToken
import com.pms.dental.domain.service.IssuedAccessToken
import com.pms.dental.domain.service.PasswordHasher
import com.pms.dental.domain.service.RefreshTokenFactory
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import java.time.Instant
import java.util.UUID

class AuthenticateUserUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val refreshTokens = mockk<RefreshTokenRepository>()
    val passwordHasher = mockk<PasswordHasher>()
    val accessTokenIssuer = mockk<AccessTokenIssuer>()
    val refreshTokenFactory = mockk<RefreshTokenFactory>()
    val now = Instant.parse("2026-07-22T10:00:00Z")
    val clock = Clock { now }
    val refreshTtl = Duration.ofDays(14)

    val authenticate = AuthenticateUserUseCase(
        users, refreshTokens, passwordHasher, accessTokenIssuer, refreshTokenFactory, clock, refreshTtl,
    )

    val user = AppUser(
        id = UUID.fromString("11111111-1111-1111-1111-111111111111"),
        email = "dentist@clinic.test",
        displayName = "Dr. Molar",
        role = Role.DENTIST,
        active = true,
        passwordHash = "stored-hash",
    )

    given("email + password authentication") {

        `when`("the email is unknown") {
            then("it is rejected with InvalidCredentials, still running a verify to equalize timing") {
                coEvery { users.findByEmail("ghost@clinic.test") } returns null
                every { passwordHasher.hash(any()) } returns "dummy-hash"
                every { passwordHasher.verify(any(), any()) } returns false

                authenticate("ghost@clinic.test", "whatever") shouldBe
                    AuthenticationResult.Rejected(AuthenticationError.InvalidCredentials)

                // The bcrypt verify runs even for an unknown email, so response time can't enumerate users.
                verify(exactly = 1) { passwordHasher.verify("whatever", "dummy-hash") }
            }
        }

        `when`("the account is a Firebase account with no local password") {
            then("it is rejected like an unknown email, running only the dummy verify for timing") {
                val firebaseUser = user.copy(
                    passwordHash = null,
                    firebaseUid = "fb-uid-123",
                    authSource = AuthSource.FIREBASE,
                )
                coEvery { users.findByEmail("dentist@clinic.test") } returns firebaseUser
                every { passwordHasher.hash(any()) } returns "dummy-hash"
                every { passwordHasher.verify(any(), any()) } returns false

                authenticate("dentist@clinic.test", "any-password") shouldBe
                    AuthenticationResult.Rejected(AuthenticationError.InvalidCredentials)

                // Only the dummy hash is ever verified — a Firebase account can never authenticate via
                // the password path, and can't be told apart from an unknown email by response time.
                verify(exactly = 1) { passwordHasher.verify("any-password", "dummy-hash") }
            }
        }

        `when`("the password does not match") {
            then("it is rejected with InvalidCredentials") {
                coEvery { users.findByEmail("dentist@clinic.test") } returns user
                every { passwordHasher.verify("wrong", "stored-hash") } returns false

                authenticate("dentist@clinic.test", "wrong") shouldBe
                    AuthenticationResult.Rejected(AuthenticationError.InvalidCredentials)
            }
        }

        `when`("credentials are correct but the account is inactive") {
            then("it is rejected with InactiveAccount") {
                coEvery { users.findByEmail("dentist@clinic.test") } returns user.copy(active = false)
                every { passwordHasher.verify("correct", "stored-hash") } returns true

                authenticate("dentist@clinic.test", "correct") shouldBe
                    AuthenticationResult.Rejected(AuthenticationError.InactiveAccount)
            }
        }

        `when`("the email has surrounding whitespace and mixed case") {
            then("it is normalized before lookup and authenticates") {
                coEvery { users.findByEmail("dentist@clinic.test") } returns user
                every { passwordHasher.verify("correct", "stored-hash") } returns true
                every { accessTokenIssuer.issue(user) } returns IssuedAccessToken("access-jwt", 900)
                every { refreshTokenFactory.newToken() } returns GeneratedRefreshToken("raw-refresh", "hash-refresh")
                coEvery { refreshTokens.store(any(), any(), any()) } just Runs

                authenticate("  Dentist@Clinic.TEST  ", "correct") shouldBe
                    AuthenticationResult.Success(user, AuthTokens("access-jwt", "raw-refresh", 900))
            }
        }

        `when`("authentication succeeds") {
            then("the rotated refresh token hash is stored with the configured expiry") {
                coEvery { users.findByEmail("dentist@clinic.test") } returns user
                every { passwordHasher.verify("correct", "stored-hash") } returns true
                every { accessTokenIssuer.issue(user) } returns IssuedAccessToken("access-jwt", 900)
                every { refreshTokenFactory.newToken() } returns GeneratedRefreshToken("raw-refresh", "hash-refresh")
                coEvery { refreshTokens.store(any(), any(), any()) } just Runs

                authenticate("dentist@clinic.test", "correct")

                coVerify(exactly = 1) {
                    refreshTokens.store(user.id, "hash-refresh", now.plus(refreshTtl))
                }
            }
        }
    }
})

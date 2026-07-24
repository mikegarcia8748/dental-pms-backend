package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthTokens
import com.pms.dental.domain.model.RefreshTokenRecord
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.GeneratedRefreshToken
import com.pms.dental.domain.service.IssuedAccessToken
import com.pms.dental.domain.service.RefreshTokenFactory
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

class RefreshAccessTokenUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val users = mockk<AppUserRepository>()
    val refreshTokens = mockk<RefreshTokenRepository>()
    val accessTokenIssuer = mockk<AccessTokenIssuer>()
    val refreshTokenFactory = mockk<RefreshTokenFactory>()
    val now = Instant.parse("2026-07-22T10:00:00Z")
    val clock = Clock { now }
    val refreshTtl = Duration.ofDays(14)

    val refresh = RefreshAccessTokenUseCase(
        users, refreshTokens, accessTokenIssuer, refreshTokenFactory, clock, refreshTtl,
    )

    val userId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    val user = AppUser(userId, "dentist@clinic.test", "Dr. Molar", Role.DENTIST, active = true, passwordHash = "h")

    // The raw token always hashes to this in these tests.
    every { refreshTokenFactory.hash("raw-token") } returns "hash-token"

    fun record(revoked: Boolean = false, expiresAt: Instant = now.plus(refreshTtl)) =
        RefreshTokenRecord(UUID.randomUUID(), userId, "hash-token", expiresAt, revoked)

    given("refreshing an access token") {

        `when`("the token is unknown") {
            then("it is rejected with InvalidToken") {
                coEvery { refreshTokens.findByHash("hash-token") } returns null
                refresh("raw-token") shouldBe RefreshResult.Rejected(RefreshError.InvalidToken)
            }
        }

        `when`("the token has been revoked") {
            then("it is rejected with Revoked and the whole token family is burned (reuse detection)") {
                coEvery { refreshTokens.findByHash("hash-token") } returns record(revoked = true)
                coEvery { refreshTokens.revokeAllForUser(userId) } just Runs

                refresh("raw-token") shouldBe RefreshResult.Rejected(RefreshError.Revoked)

                coVerify(exactly = 1) { refreshTokens.revokeAllForUser(userId) }
            }
        }

        `when`("the token has expired") {
            then("it is rejected with Expired") {
                coEvery { refreshTokens.findByHash("hash-token") } returns
                    record(expiresAt = now.minusSeconds(1))
                refresh("raw-token") shouldBe RefreshResult.Rejected(RefreshError.Expired)
            }
        }

        `when`("the token owner no longer exists") {
            then("it is rejected with InvalidToken") {
                coEvery { refreshTokens.findByHash("hash-token") } returns record()
                coEvery { users.findById(userId) } returns null
                refresh("raw-token") shouldBe RefreshResult.Rejected(RefreshError.InvalidToken)
            }
        }

        `when`("the token owner is deactivated") {
            then("it is rejected with InactiveAccount") {
                coEvery { refreshTokens.findByHash("hash-token") } returns record()
                coEvery { users.findById(userId) } returns user.copy(active = false)
                refresh("raw-token") shouldBe RefreshResult.Rejected(RefreshError.InactiveAccount)
            }
        }

        `when`("the token is valid") {
            then("it returns a new token pair") {
                coEvery { refreshTokens.findByHash("hash-token") } returns record()
                coEvery { users.findById(userId) } returns user
                coEvery { refreshTokens.revoke(any()) } just Runs
                coEvery { refreshTokens.store(any(), any(), any()) } just Runs
                every { accessTokenIssuer.issue(user) } returns IssuedAccessToken("new-access", 900)
                every { refreshTokenFactory.newToken() } returns GeneratedRefreshToken("new-raw", "new-hash")

                val result = refresh("raw-token")

                result.shouldBeInstanceOf<RefreshResult.Success>()
                result.tokens shouldBe AuthTokens("new-access", "new-raw", 900)
            }
        }

        `when`("the token is valid and rotation is persisted") {
            then("the old token is revoked and the new hash is stored") {
                coEvery { refreshTokens.findByHash("hash-token") } returns record()
                coEvery { users.findById(userId) } returns user
                coEvery { refreshTokens.revoke(any()) } just Runs
                coEvery { refreshTokens.store(any(), any(), any()) } just Runs
                every { accessTokenIssuer.issue(user) } returns IssuedAccessToken("new-access", 900)
                every { refreshTokenFactory.newToken() } returns GeneratedRefreshToken("new-raw", "new-hash")

                refresh("raw-token")

                coVerify(exactly = 1) { refreshTokens.revoke("hash-token") }
                coVerify(exactly = 1) { refreshTokens.store(userId, "new-hash", now.plus(refreshTtl)) }
            }
        }
    }
})

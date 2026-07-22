package com.pms.dental.domain.usecase

import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.RefreshTokenFactory
import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk

class LogoutUseCaseTest : BehaviorSpec({

    isolationMode = IsolationMode.InstancePerLeaf

    val refreshTokens = mockk<RefreshTokenRepository>()
    val refreshTokenFactory = mockk<RefreshTokenFactory>()
    val logout = LogoutUseCase(refreshTokens, refreshTokenFactory)

    given("logging out") {

        `when`("a refresh token is presented") {
            then("it revokes the token's hash, not the raw value") {
                every { refreshTokenFactory.hash("raw-token") } returns "hash-token"
                coEvery { refreshTokens.revoke(any()) } just Runs

                logout("raw-token")

                coVerify(exactly = 1) { refreshTokens.revoke("hash-token") }
            }
        }

        `when`("the token is unknown to the store") {
            then("it still calls revoke and does not throw (idempotent)") {
                every { refreshTokenFactory.hash("stale-token") } returns "stale-hash"
                coEvery { refreshTokens.revoke("stale-hash") } just Runs

                logout("stale-token")

                coVerify(exactly = 1) { refreshTokens.revoke("stale-hash") }
            }
        }
    }
})

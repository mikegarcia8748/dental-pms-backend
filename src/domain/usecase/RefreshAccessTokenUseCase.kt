package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthTokens
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.RefreshTokenFactory
import java.time.Duration

sealed interface RefreshResult {
    data class Success(val user: AppUser, val tokens: AuthTokens) : RefreshResult
    data class Rejected(val error: RefreshError) : RefreshResult
}

enum class RefreshError {
    /** No such token (or it belongs to a user that no longer exists). */
    InvalidToken,
    Expired,
    Revoked,
    InactiveAccount,
}

/**
 * Business rule: exchange a valid refresh token for a new access token, rotating the refresh
 * token in the process (the presented token is revoked and a fresh one is issued and stored).
 * Rejects tokens that are unknown, revoked, expired, or whose owner is deactivated.
 */
class RefreshAccessTokenUseCase(
    private val users: AppUserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenFactory: RefreshTokenFactory,
    private val clock: Clock,
    private val refreshTokenTtl: Duration,
) {
    suspend operator fun invoke(rawRefreshToken: String): RefreshResult {
        val hash = refreshTokenFactory.hash(rawRefreshToken)
        val record = refreshTokens.findByHash(hash)
            ?: return RefreshResult.Rejected(RefreshError.InvalidToken)

        if (record.revoked) return RefreshResult.Rejected(RefreshError.Revoked)
        if (!record.expiresAt.isAfter(clock.now())) return RefreshResult.Rejected(RefreshError.Expired)

        val user = users.findById(record.userId)
            ?: return RefreshResult.Rejected(RefreshError.InvalidToken)
        if (!user.active) return RefreshResult.Rejected(RefreshError.InactiveAccount)

        // Rotate: burn the presented token and mint a fresh one.
        refreshTokens.revoke(hash)
        val access = accessTokenIssuer.issue(user)
        val newRefresh = refreshTokenFactory.newToken()
        refreshTokens.store(user.id, newRefresh.hash, clock.now().plus(refreshTokenTtl))

        return RefreshResult.Success(
            user,
            AuthTokens(access.token, newRefresh.raw, access.expiresInSeconds),
        )
    }
}

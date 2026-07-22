package com.pms.dental.domain.usecase

import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.RefreshTokenFactory

/**
 * Business rule: log out by revoking the presented refresh token. Idempotent — revoking an
 * unknown token is a harmless no-op — and it revokes the token's *hash*, never the raw value.
 */
class LogoutUseCase(
    private val refreshTokens: RefreshTokenRepository,
    private val refreshTokenFactory: RefreshTokenFactory,
) {
    suspend operator fun invoke(rawRefreshToken: String) {
        refreshTokens.revoke(refreshTokenFactory.hash(rawRefreshToken))
    }
}

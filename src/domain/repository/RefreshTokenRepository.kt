package com.pms.dental.domain.repository

import com.pms.dental.domain.model.RefreshTokenRecord
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository {
    suspend fun store(userId: UUID, tokenHash: String, expiresAt: Instant)
    /** Looks a token up by its stored hash; returns null if no such token exists. */
    suspend fun findByHash(tokenHash: String): RefreshTokenRecord?
    suspend fun revoke(tokenHash: String)
    suspend fun revokeAllForUser(userId: UUID)
    /** Housekeeping: drop tokens that expired on or before [now]. */
    suspend fun deleteExpired(now: Instant)
}

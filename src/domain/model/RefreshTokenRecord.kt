package com.pms.dental.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A persisted refresh token. Only the [tokenHash] is stored — never the raw token. The use
 * cases read [revoked] and [expiresAt] to distinguish a revoked token from an expired one,
 * so both fields are exposed rather than pre-filtered by the repository.
 */
data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val revoked: Boolean,
)

package com.pms.dental.domain.model

/**
 * The credential pair returned to a client after a successful login or refresh. The
 * [accessToken] is a short-lived JWT; the [refreshToken] is the raw opaque token whose hash
 * is persisted server-side so it can be rotated and revoked.
 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessTokenExpiresInSeconds: Long,
)

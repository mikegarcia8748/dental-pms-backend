package com.pms.dental.domain.service

/** A freshly minted refresh token: [raw] goes to the client once, only [hash] is stored. */
data class GeneratedRefreshToken(val raw: String, val hash: String)

/**
 * Mints opaque refresh tokens and hashes incoming ones for lookup. Generation and hashing
 * live together so the same one-way transform is used to store and to find a token.
 */
interface RefreshTokenFactory {
    fun newToken(): GeneratedRefreshToken
    fun hash(rawToken: String): String
}

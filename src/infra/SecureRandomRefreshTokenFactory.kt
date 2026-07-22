package com.pms.dental.infra

import com.pms.dental.domain.service.GeneratedRefreshToken
import com.pms.dental.domain.service.RefreshTokenFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Generates opaque 256-bit refresh tokens and hashes them with SHA-256 for storage. The raw
 * token is high-entropy random (not a secret-derived value), so a fast one-way hash is the
 * right choice for lookup — unlike passwords, there is nothing to brute-force.
 */
class SecureRandomRefreshTokenFactory(
    private val random: SecureRandom = SecureRandom(),
) : RefreshTokenFactory {

    private val encoder: Base64.Encoder = Base64.getUrlEncoder().withoutPadding()

    override fun newToken(): GeneratedRefreshToken {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val raw = encoder.encodeToString(bytes)
        return GeneratedRefreshToken(raw, hash(raw))
    }

    override fun hash(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(rawToken.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val TOKEN_BYTES = 32
    }
}

package com.pms.dental.infra

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IssuedAccessToken
import java.util.Date

/**
 * Issues HMAC-signed JWT access tokens. The role travels as a `role` claim, which the Ktor
 * auth layer reads back to build the principal and enforce authorization.
 */
class JwtAccessTokenIssuer(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val accessTtlSeconds: Long,
    private val clock: Clock,
) : AccessTokenIssuer {

    private val algorithm: Algorithm = Algorithm.HMAC256(secret)

    override fun issue(user: AppUser): IssuedAccessToken {
        val issuedAt = clock.now()
        val expiresAt = issuedAt.plusSeconds(accessTtlSeconds)
        val token = JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(user.id.toString())
            .withClaim("role", user.role.name)
            .withClaim("email", user.email)
            .withClaim("name", user.displayName)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
        return IssuedAccessToken(token, accessTtlSeconds)
    }
}

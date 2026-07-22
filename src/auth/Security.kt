package com.pms.dental.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.model.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond

/**
 * Installs JWT verification under the name "auth-jwt": checks signature, issuer, and audience,
 * and only accepts tokens whose `role` claim is a known [Role] and whose subject is present.
 */
fun Application.configureSecurity(config: AuthConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = config.jwtIssuer
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build(),
            )
            validate { credential ->
                val role = credential.payload.getClaim("role").asString()
                val hasKnownRole = role != null && runCatching { Role.valueOf(role) }.isSuccess
                if (!credential.payload.subject.isNullOrBlank() && hasKnownRole) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing or invalid token"),
                )
            }
        }
    }
}

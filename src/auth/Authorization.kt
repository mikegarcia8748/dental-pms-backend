package com.pms.dental.auth

import com.pms.dental.domain.model.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import java.util.UUID

/** The identity carried by a verified access token, decoded from its JWT claims. */
data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: Role,
)

/** Decodes the authenticated user from the JWT principal, or null if unauthenticated/malformed. */
val ApplicationCall.authenticatedUser: AuthenticatedUser?
    get() {
        val payload = principal<JWTPrincipal>()?.payload ?: return null
        val id = runCatching { UUID.fromString(payload.subject) }.getOrNull() ?: return null
        val role = runCatching { Role.valueOf(payload.getClaim("role").asString()) }.getOrNull() ?: return null
        return AuthenticatedUser(
            id = id,
            email = payload.getClaim("email").asString().orEmpty(),
            displayName = payload.getClaim("name").asString().orEmpty(),
            role = role,
        )
    }

class RoleAuthorizationConfig {
    var allowed: Set<Role> = emptySet()
}

/**
 * Route-scoped guard that runs after authentication: rejects an authenticated user whose role
 * is not in [RoleAuthorizationConfig.allowed] with 403. Responding in the AuthenticationChecked
 * hook short-circuits the pipeline, so the guarded handler never runs.
 */
val RoleAuthorization = createRouteScopedPlugin("RoleAuthorization", ::RoleAuthorizationConfig) {
    val allowed = pluginConfig.allowed
    on(AuthenticationChecked) { call ->
        val user = call.authenticatedUser
        when {
            user == null ->
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Authentication required"))
            allowed.isNotEmpty() && user.role !in allowed ->
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponse("forbidden", "Requires role: ${allowed.joinToString(", ") { it.name }}"),
                )
        }
    }
}

/**
 * Reusable RBAC primitive: require a verified access token (401 otherwise) and, when [roles] are
 * given, require one of them (403 otherwise). Later clinical routes hang off this.
 */
fun Route.authorize(vararg roles: Role, build: Route.() -> Unit) {
    authenticate("auth-jwt") {
        install(RoleAuthorization) { allowed = roles.toSet() }
        build()
    }
}

package com.pms.dental.auth

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.Role
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.auth.AuthenticationChecked
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import java.util.UUID

/** The identity carried by a verified request, resolved from the Neon `app_user` row (not the token). */
data class AuthenticatedUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: Role,
)

/** Projects a persisted account onto the request-scoped identity. Role and active status live here. */
internal fun AppUser.toAuthenticatedUser() = AuthenticatedUser(id, email, displayName, role)

/** The user set by whichever provider (auth-local or auth-firebase) verified the request. */
val ApplicationCall.authenticatedUser: AuthenticatedUser?
    get() = principal<AuthenticatedUser>()

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
 * Reusable RBAC primitive: require a verified request via either provider (401 otherwise) and, when
 * [roles] are given, require one of them (403 otherwise). Clinical and admin routes hang off this.
 */
fun Route.authorize(vararg roles: Role, build: Route.() -> Unit) {
    authenticate("auth-local", "auth-firebase") {
        install(RoleAuthorization) { allowed = roles.toSet() }
        build()
    }
}

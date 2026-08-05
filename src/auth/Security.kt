package com.pms.dental.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.bearer
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.response.respond
import java.util.UUID

/**
 * Installs the two authentication providers the API accepts. Both resolve the caller from Neon, so
 * role and active status always come from the database — never trusted from the token:
 *
 *  - **auth-local** — the self-hosted HMAC-SHA256 JWT for the LOCAL break-glass account. After the
 *    signature/issuer/audience checks, the subject (a UUID) is looked up in `app_user`.
 *  - **auth-firebase** — a Firebase ID token from the front-end's "Sign in with Google" button
 *    (RS256, verified against Google's public JWKS — no service-account credentials). Resolving it
 *    to a local account, including claiming an unclaimed staff invite on a first sign-in, is
 *    [AuthenticateFirebaseUserUseCase]'s job; a token that resolves to nothing is rejected, and
 *    there is no auto-provisioning.
 *
 * That use case verifies through
 * [com.pms.dental.domain.service.PolicyCheckingFirebaseTokenVerifier], so a token that is authentic
 * but unacceptable — unverified email, a sign-in provider outside the allowlist, a sign-in older
 * than the max session age — is indistinguishable here from a forged one. That is deliberate: the
 * client gets one opaque 401 and the reason goes to the log.
 *
 * Either way, a missing row or an inactive account yields no principal (→ 401). Reading `active`
 * per request is the instant-revocation lever: deactivating an account blocks it immediately,
 * regardless of any still-valid token's remaining lifetime — and it is the *only* such lever now
 * that disabling the Firebase user itself needs credentials this backend does not hold.
 */
fun Application.configureSecurity(
    config: AuthConfig,
    users: AppUserRepository,
    firebaseSignIn: AuthenticateFirebaseUserUseCase,
) {
    install(Authentication) {
        jwt("auth-local") {
            realm = config.jwtIssuer
            verifier(
                JWT.require(Algorithm.HMAC256(config.jwtSecret))
                    .withIssuer(config.jwtIssuer)
                    .withAudience(config.jwtAudience)
                    .build(),
            )
            validate { credential ->
                val id = runCatching { UUID.fromString(credential.payload.subject) }.getOrNull()
                    ?: return@validate null
                users.findById(id)?.takeIf { it.active }?.toAuthenticatedUser()
            }
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse("unauthorized", "Missing or invalid token"),
                )
            }
        }
        bearer("auth-firebase") {
            realm = config.jwtIssuer
            authenticate { credential -> firebaseSignIn(credential.token)?.toAuthenticatedUser() }
            // `bearer` has no configurable challenge — on its own it emits an empty-body 401. The
            // JSON error body clients actually receive comes from auth-local's challenge above,
            // because `authorize()` registers auth-local first and Ktor's challenge sort is stable.
            // Swapping that order in Authorization.kt would silently empty the body; AuthRoutesTest
            // asserts the exact body to catch it.
        }
    }
}

package com.pms.dental.domain.service

import java.time.Instant

/**
 * Verifies a Firebase ID token. Deliberately free of Firebase SDK types so use cases, the auth
 * stage, and tests depend only on this abstraction (mirrors [AccessTokenIssuer] / its infra impl).
 */
interface FirebaseTokenVerifier {
    /**
     * Verifies [idToken] — that it is authentic and unexpired, nothing more.
     *
     * There is deliberately no `checkRevoked` option. Asking Firebase whether a session was revoked
     * is a privileged Admin SDK call, and this backend holds no service-account credentials by
     * design. Revocation is instead the per-request `active` read against Neon in `configureSecurity`,
     * which is instant and does not depend on Google being reachable.
     */
    suspend fun verify(idToken: String): FirebaseVerification
}

/**
 * Outcome of verifying a Firebase ID token.
 *
 * [Valid] means only that the token is *authentic* — correctly signed by Firebase for our project
 * and unexpired. Whether it is **acceptable** is a separate question answered by
 * [FirebaseTokenPolicy], which reads the claims below. Keep this type a faithful description of
 * the token; put judgements in the policy.
 */
sealed interface FirebaseVerification {
    /** A valid token. [firebaseUid] is the immutable subject used to join to the local account. */
    data class Valid(
        val firebaseUid: String,
        val email: String?,
        val emailVerified: Boolean,
        /**
         * The `firebase.sign_in_provider` claim — e.g. `password`, `google.com`, `anonymous`.
         * Null when the claim is absent, which the policy treats as a rejection (fail closed).
         */
        val signInProvider: String?,
        /**
         * The `auth_time` claim: when the user originally signed in, **not** when this token was
         * minted. The client SDK refreshes the hourly ID token without moving `auth_time`, so this
         * is what a max-session-age rule must measure. Null when the claim is absent.
         */
        val authTime: Instant?,
    ) : FirebaseVerification

    /** Missing, malformed, expired, wrong signature/issuer/audience, revoked, or Firebase disabled. */
    data object Invalid : FirebaseVerification
}

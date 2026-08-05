package com.pms.dental.domain.service

import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Why a token that Firebase itself considers valid is still not good enough for this API. Never
 * reaches the wire — every rejection is the same opaque 401, so an attacker learns nothing about
 * which rule they tripped. The reason exists so an operator can answer "why is this user getting
 * 401s?" from the logs.
 */
enum class FirebasePolicyRejection {
    /** `email_verified` is false: the address was never proven to belong to the signer. */
    EmailNotVerified,

    /** `firebase.sign_in_provider` is absent, or not on the configured allowlist. */
    UnsupportedProvider,

    /** A max session age is configured but the token carries no `auth_time` to measure against. */
    MissingAuthTime,

    /** The original sign-in is older than the configured max session age; re-authentication required. */
    SessionTooOld,
}

/** Outcome of applying [FirebaseTokenPolicy]. Failures are data, as elsewhere in the domain. */
sealed interface FirebasePolicyDecision {
    data object Accepted : FirebasePolicyDecision

    data class Rejected(val reason: FirebasePolicyRejection) : FirebasePolicyDecision
}

/**
 * The claims a Firebase ID token must satisfy before this API will act on it.
 *
 * Verifying a token proves Firebase minted it for our project; it does not say the identity behind
 * it is one we accept. Firebase will happily mint a token for an anonymous session, an unverified
 * email address, or a sign-in that happened three weeks ago — this is where we say no.
 *
 * Deliberately pure and SDK-free so every rule is unit-testable with a pinned [Clock]. It reads
 * only the token; role and active status still come from Neon on every request, never from claims.
 *
 * **Missing claims fail closed.** A token with no `sign_in_provider`, or none with `auth_time`
 * while a max age is configured, is rejected rather than waved through — an absent claim is not
 * evidence that the rule is satisfied.
 */
class FirebaseTokenPolicy(
    private val requireVerifiedEmail: Boolean,
    private val allowedSignInProviders: Set<String>,
    private val maxSessionAge: Duration?,
    private val clock: Clock,
) {
    fun evaluate(token: FirebaseVerification.Valid): FirebasePolicyDecision {
        if (requireVerifiedEmail && !token.emailVerified) {
            return FirebasePolicyDecision.Rejected(FirebasePolicyRejection.EmailNotVerified)
        }
        if (token.signInProvider == null || token.signInProvider !in allowedSignInProviders) {
            return FirebasePolicyDecision.Rejected(FirebasePolicyRejection.UnsupportedProvider)
        }
        if (maxSessionAge != null) {
            val authTime = token.authTime
                ?: return FirebasePolicyDecision.Rejected(FirebasePolicyRejection.MissingAuthTime)
            // A future auth_time yields a negative age, which passes — small clock skew between
            // Firebase and this server should not lock a freshly signed-in user out.
            if (Duration.between(authTime, clock.now()) > maxSessionAge) {
                return FirebasePolicyDecision.Rejected(FirebasePolicyRejection.SessionTooOld)
            }
        }
        return FirebasePolicyDecision.Accepted
    }
}

/**
 * Applies [FirebaseTokenPolicy] to whatever [delegate] verified, collapsing a policy rejection to
 * [FirebaseVerification.Invalid] — so the auth stage, and therefore the client, cannot tell a
 * policy rejection from a forged token. The reason goes to the log instead.
 *
 * Wrapping the port rather than checking in the auth stage means the policy holds for *every*
 * consumer of [FirebaseTokenVerifier], not just the one call site in `configureSecurity`.
 */
class PolicyCheckingFirebaseTokenVerifier(
    private val delegate: FirebaseTokenVerifier,
    private val policy: FirebaseTokenPolicy,
) : FirebaseTokenVerifier {

    private val log = LoggerFactory.getLogger(javaClass)

    override suspend fun verify(idToken: String): FirebaseVerification {
        val verified = delegate.verify(idToken)
        if (verified !is FirebaseVerification.Valid) return verified

        return when (val decision = policy.evaluate(verified)) {
            FirebasePolicyDecision.Accepted -> verified
            is FirebasePolicyDecision.Rejected -> {
                // The uid is safe to log and is what an operator needs to trace the account; the
                // token itself is a live credential and must never be logged.
                log.warn(
                    "Rejected an authentic Firebase ID token for uid {}: {}",
                    verified.firebaseUid,
                    decision.reason,
                )
                FirebaseVerification.Invalid
            }
        }
    }
}

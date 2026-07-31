package com.pms.dental.support

import com.pms.dental.domain.service.FirebaseTokenVerifier
import com.pms.dental.domain.service.FirebaseVerification
import java.time.Instant

/**
 * In-memory Firebase token verifier for route tests. Only tokens explicitly registered via
 * [accept] verify; everything else is [FirebaseVerification.Invalid] — mirroring how a real
 * verifier rejects unknown/forged tokens and how a disabled Firebase rejects everything.
 *
 * Like the real verifier, this fake reports only that a token is *authentic*. Claim policy is
 * `PolicyCheckingFirebaseTokenVerifier`'s job; wrap this fake in it to exercise the rules.
 */
class FakeFirebaseTokenVerifier : FirebaseTokenVerifier {
    private val valid = mutableMapOf<String, FirebaseVerification.Valid>()

    /**
     * Register [token] as a valid Firebase ID token that resolves to [firebaseUid].
     *
     * The claim defaults describe an ordinary staff sign-in: a verified email, the `password`
     * provider, and a just-now [authTime] — so a token from this fake satisfies the default policy
     * whatever clock the test wires up. Override them to build a token that should be rejected.
     */
    fun accept(
        token: String,
        firebaseUid: String,
        email: String? = null,
        emailVerified: Boolean = true,
        signInProvider: String? = "password",
        authTime: Instant? = Instant.now(),
    ) {
        valid[token] = FirebaseVerification.Valid(firebaseUid, email, emailVerified, signInProvider, authTime)
    }

    override suspend fun verify(idToken: String): FirebaseVerification =
        valid[idToken] ?: FirebaseVerification.Invalid
}

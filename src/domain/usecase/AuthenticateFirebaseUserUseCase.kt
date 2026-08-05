package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.service.FirebaseTokenVerifier
import com.pms.dental.domain.service.FirebaseVerification
import org.slf4j.LoggerFactory

/**
 * Business rule: resolve the local account behind a Firebase ID token, returning null when the
 * caller should get a 401.
 *
 * Two paths converge here:
 *
 *  - **Steady state.** The token's UID is already bound to an `app_user` row; look it up and check
 *    `active`. This is every request after the first.
 *  - **First sign-in.** Because the backend cannot create Firebase users (no service-account
 *    credentials by design), a provisioned staff member starts as an *unclaimed invite* — a row with
 *    their email and no UID. The first time they press "Sign in with Google", their Google-verified
 *    email is matched to that invite and the UID is bound, once.
 *
 * After the bind, the join is UID-only forever, which is what keeps a later email change harmless.
 * The email is used exactly once, as the thing the invite was addressed to.
 */
class AuthenticateFirebaseUserUseCase(
    private val verifier: FirebaseTokenVerifier,
    private val users: AppUserRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend operator fun invoke(idToken: String): AppUser? {
        val token = verifier.verify(idToken) as? FirebaseVerification.Valid ?: return null

        users.findByFirebaseUid(token.firebaseUid)?.let { return it.takeIf { user -> user.active } }

        return claimInvite(token)
    }

    /**
     * Binds [token]'s UID to a matching unclaimed invite, or returns null.
     *
     * Every condition here is load-bearing; each rejection is silent to the caller (one opaque 401)
     * and logged for the operator.
     */
    private suspend fun claimInvite(token: FirebaseVerification.Valid): AppUser? {
        // Re-asserted here even though FirebaseTokenPolicy normally enforces it, because this is the
        // one place where the *email* grants access rather than merely describing the account.
        // Turning FIREBASE_REQUIRE_VERIFIED_EMAIL off must never become "any Google account may
        // claim any invite by asserting its address".
        if (!token.emailVerified) {
            log.warn("Refusing to claim a staff invite for uid {}: the token's email is unverified", token.firebaseUid)
            return null
        }
        val email = token.email?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        if (email == null) {
            log.warn("Cannot claim a staff invite for uid {}: the token carries no email claim", token.firebaseUid)
            return null
        }

        val invite = users.findByEmail(email)
        if (invite == null) {
            // By far the most common cause of "Google sign-in worked but every request 401s": the
            // person authenticated to Firebase fine, but nobody ever invited them here. Say so, and
            // name the remedy — an unprovisioned account and a forged token are the same 401 on the
            // wire, so this log line is the only thing that tells them apart.
            log.warn(
                "No staff invite for {}: the account authenticated with Google but has never been " +
                    "provisioned. Invite it with POST /admin/staff using this exact address.",
                email,
            )
            return null
        }
        when {
            invite.authSource != AuthSource.FIREBASE -> {
                // A LOCAL break-glass account is never convertible to a Firebase identity.
                log.warn("Refusing to claim invite for {}: it is a LOCAL break-glass account", email)
                return null
            }
            invite.firebaseUid != null -> {
                // Already claimed by a different Google identity — the UID lookup above would have
                // matched otherwise. Binding a second identity to one account is never right.
                log.warn("Refusing to claim invite for {}: already bound to a different Firebase identity", email)
                return null
            }
            !invite.active -> {
                // Offboarded before ever signing in. Deliberately not claimable: binding the uid now
                // would leave a live identity attached to a deactivated account.
                log.warn("Refusing to claim invite for {}: the account is deactivated", email)
                return null
            }
        }

        if (users.bindFirebaseUid(invite.id, token.firebaseUid)) {
            log.info("Staff invite for {} claimed by Firebase uid {}", email, token.firebaseUid)
            return invite.copy(firebaseUid = token.firebaseUid)
        }

        // Lost a race with a concurrent first sign-in. Whoever won wrote a UID; re-read to find out
        // whether it was this same identity (a duplicate request) or a different one (reject).
        return users.findByFirebaseUid(token.firebaseUid)?.takeIf { it.active }
    }
}

package com.pms.dental.domain.model

/**
 * Which system owns an [AppUser]'s credentials.
 *
 * The two paths never overlap on a single account, and the split is enforced both by the
 * `app_user_auth_source_ck` DB constraint and by the login use case.
 */
enum class AuthSource {
    /** Self-hosted email + password — the always-available break-glass path. Has a `passwordHash`. */
    LOCAL,

    /** Firebase Authentication — identified by an immutable `firebaseUid`; carries no local password. */
    FIREBASE,
}

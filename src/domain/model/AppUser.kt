package com.pms.dental.domain.model

import java.util.UUID

/**
 * An account that can sign in.
 *
 * [authSource] decides how the account authenticates:
 *  - [AuthSource.LOCAL] — email + password; [passwordHash] holds the bcrypt hash (the raw
 *    password never lives in the domain) and [firebaseUid] is null. This is the break-glass path.
 *  - [AuthSource.FIREBASE] — the user authenticates via Firebase; [firebaseUid] is the immutable
 *    join key from a verified Firebase ID token and [passwordHash] is null.
 *
 * [active] gates authentication regardless of source: an inactive user is rejected even with an
 * otherwise-valid credential. It is checked per request, so it is the instant-revocation lever.
 */
data class AppUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val passwordHash: String?,
    val firebaseUid: String? = null,
    val authSource: AuthSource = AuthSource.LOCAL,
)

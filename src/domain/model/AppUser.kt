package com.pms.dental.domain.model

import java.util.UUID

/**
 * An account that can sign in. [passwordHash] is the stored bcrypt hash — the raw password
 * never lives in the domain. [active] gates authentication: an inactive user cannot log in
 * or refresh, even with otherwise-valid credentials.
 */
data class AppUser(
    val id: UUID,
    val email: String,
    val displayName: String,
    val role: Role,
    val active: Boolean,
    val passwordHash: String,
)

package com.pms.dental.admin

import com.pms.dental.domain.model.Role

/** Max email length; matches the `app_user.email VARCHAR(320)` column. */
private const val MAX_EMAIL = 320

/** Max display name length; matches the `app_user.display_name VARCHAR(200)` column. */
private const val MAX_DISPLAY_NAME = 200

/**
 * Returns the first validation problem with a provisioning request, or null when it is valid.
 *
 * The length caps are not cosmetic: provisioning creates the Firebase user *before* inserting the
 * local row, so an over-long value used to pass validation, mint a Firebase identity, and then blow
 * up on the Postgres insert — a 500 with a Firebase user left behind.
 *
 * Checks run against the **trimmed** values, because that is what `ProvisionStaffUseCase` actually
 * stores; validating the raw input would let "   " through as a display name.
 */
fun ProvisionStaffRequest.validationError(): String? {
    val email = email.trim()
    val displayName = displayName.trim()
    return when {
        email.isBlank() -> "email is required"
        email.length > MAX_EMAIL -> "email must be at most $MAX_EMAIL characters"
        !email.contains("@") -> "email must be a valid email address"
        displayName.isBlank() -> "displayName is required"
        displayName.length > MAX_DISPLAY_NAME -> "displayName must be at most $MAX_DISPLAY_NAME characters"
        runCatching { Role.valueOf(role) }.isFailure ->
            "role must be one of: ${Role.entries.joinToString(", ") { it.name }}"
        else -> null
    }
}

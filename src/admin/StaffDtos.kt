package com.pms.dental.admin

import kotlinx.serialization.Serializable

/**
 * Request to invite a staff member. No password — they sign in with Google. The email must be
 * exactly the address of the Google account they will use; it is what binds them to this account.
 */
@Serializable
data class ProvisionStaffRequest(
    val email: String,
    val displayName: String,
    val role: String,
)

/** A staff account as seen by the admin views. `firebaseUid` is internal and not exposed here. */
@Serializable
data class StaffResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
    val active: Boolean,
    val authSource: String,
    /**
     * False while a Firebase invite is unclaimed — the account exists but nobody has signed in with
     * Google as this email yet. Always true for a LOCAL break-glass account. This is the only way a
     * SysAdmin can tell "invited" from "actually onboarded", since provisioning no longer touches
     * Firebase.
     */
    val signedIn: Boolean,
)

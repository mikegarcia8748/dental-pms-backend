package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.service.IdGenerator

/** Outcome of provisioning a staff member. Failures are data so the route maps them to HTTP status. */
sealed interface ProvisionStaffResult {
    data class Provisioned(val user: AppUser) : ProvisionStaffResult

    /** The email is already a Firebase staff account — the operation is a no-op (idempotent). */
    data object AlreadyProvisioned : ProvisionStaffResult

    /** The email belongs to a LOCAL (break-glass) account; never silently convert it to Firebase. */
    data object LocalAccountExists : ProvisionStaffResult
}

/**
 * Business rule: invite a staff member. Writes the `app_user` row that grants them access — email,
 * role, active — with **no Firebase UID yet**.
 *
 * This is purely a Neon write. The backend cannot create Firebase users: that is a privileged Admin
 * SDK operation and we hold no service-account credentials by design. Instead the row sits unclaimed
 * until the staff member presses "Sign in with Google"; on that first request
 * [AuthenticateFirebaseUserUseCase] matches their Google-verified email to this invite and binds the
 * UID once, after which every request joins on the UID alone.
 *
 * The consequence worth knowing: the email here must be **exactly** the address of the Google
 * account they will sign in with, and nothing tells them they have been invited — that is an
 * out-of-band conversation.
 */
class ProvisionStaffUseCase(
    private val users: AppUserRepository,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(email: String, displayName: String, role: Role): ProvisionStaffResult {
        val normalizedEmail = email.trim().lowercase()
        val normalizedDisplayName = displayName.trim()

        users.findByEmail(normalizedEmail)?.let { existing ->
            return when (existing.authSource) {
                AuthSource.FIREBASE -> ProvisionStaffResult.AlreadyProvisioned
                AuthSource.LOCAL -> ProvisionStaffResult.LocalAccountExists
            }
        }

        val user = AppUser(
            id = idGenerator.newId(),
            email = normalizedEmail,
            displayName = normalizedDisplayName,
            role = role,
            active = true,
            passwordHash = null,
            // Unclaimed: filled in by the first Google sign-in that presents this verified email.
            firebaseUid = null,
            authSource = AuthSource.FIREBASE,
        )
        users.insert(user)

        return ProvisionStaffResult.Provisioned(user)
    }
}

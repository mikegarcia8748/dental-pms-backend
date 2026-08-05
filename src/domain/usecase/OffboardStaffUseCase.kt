package com.pms.dental.domain.usecase

import com.pms.dental.domain.repository.AppUserRepository
import java.util.UUID

sealed interface OffboardStaffResult {
    data object Offboarded : OffboardStaffResult
    data object NotFound : OffboardStaffResult
}

/**
 * Business rule: offboard a staff member by flipping `active` to false in Neon.
 *
 * That single write **is** the revocation: role and active status are read from Neon on every
 * request, so the next call fails with a 401 no matter how much life the caller's Google ID token
 * has left. Their Google account keeps existing and can still sign in to Google — it simply reaches
 * nothing here. Disabling the Firebase user itself is a privileged Admin SDK operation and this
 * backend deliberately holds no service-account credentials.
 */
class OffboardStaffUseCase(private val users: AppUserRepository) {
    suspend operator fun invoke(id: UUID): OffboardStaffResult {
        users.findById(id) ?: return OffboardStaffResult.NotFound
        users.setActive(id, false)
        return OffboardStaffResult.Offboarded
    }
}

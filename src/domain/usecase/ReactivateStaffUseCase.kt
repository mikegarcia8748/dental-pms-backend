package com.pms.dental.domain.usecase

import com.pms.dental.domain.repository.AppUserRepository
import java.util.UUID

sealed interface ReactivateStaffResult {
    data object Reactivated : ReactivateStaffResult
    data object NotFound : ReactivateStaffResult
}

/**
 * Business rule: re-enable a previously offboarded staff member — the inverse of
 * [OffboardStaffUseCase]. Restoring Neon `active` restores access on the next request; if they had
 * already claimed their invite, their existing Google identity still maps to this row.
 */
class ReactivateStaffUseCase(private val users: AppUserRepository) {
    suspend operator fun invoke(id: UUID): ReactivateStaffResult {
        users.findById(id) ?: return ReactivateStaffResult.NotFound
        users.setActive(id, true)
        return ReactivateStaffResult.Reactivated
    }
}

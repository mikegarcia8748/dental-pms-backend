package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.repository.AppUserRepository

/**
 * Business rule: list staff accounts from Neon (the source of truth for role and active status).
 * [activeOnly] filters by the active flag; null returns everyone.
 */
class ListStaffUseCase(private val users: AppUserRepository) {
    suspend operator fun invoke(activeOnly: Boolean? = null): List<AppUser> = users.list(activeOnly)
}

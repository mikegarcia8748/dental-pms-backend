package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.repository.AppUserRepository
import java.util.UUID

/** Business rule: fetch a single staff account by id, or null if there is none. */
class GetStaffUseCase(private val users: AppUserRepository) {
    suspend operator fun invoke(id: UUID): AppUser? = users.findById(id)
}

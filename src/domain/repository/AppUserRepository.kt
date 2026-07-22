package com.pms.dental.domain.repository

import com.pms.dental.domain.model.AppUser
import java.util.UUID

interface AppUserRepository {
    suspend fun findByEmail(email: String): AppUser?
    suspend fun findById(id: UUID): AppUser?
    suspend fun insert(user: AppUser)
    /** Total accounts, used by first-run bootstrap to decide whether to seed. */
    suspend fun countAll(): Long
}

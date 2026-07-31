package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.service.IdGenerator
import com.pms.dental.domain.service.PasswordHasher

/** A first-run account to seed, described in raw form before hashing. */
data class BootstrapAccount(
    val email: String,
    val rawPassword: String,
    val displayName: String,
    val role: Role,
)

sealed interface BootstrapResult {
    data class Seeded(val createdCount: Int) : BootstrapResult
    data object Skipped : BootstrapResult
}

/**
 * Business rule: on first run only (the user table is empty) seed the configured accounts,
 * hashing their passwords and marking them active. If any account already exists, or nothing
 * is configured to seed, it does nothing — so restarts never duplicate accounts.
 */
class BootstrapUsersUseCase(
    private val users: AppUserRepository,
    private val passwordHasher: PasswordHasher,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(accounts: List<BootstrapAccount>): BootstrapResult {
        if (accounts.isEmpty() || users.countAll() > 0) return BootstrapResult.Skipped

        accounts.forEach { account ->
            users.insert(
                AppUser(
                    id = idGenerator.newId(),
                    email = account.email.trim().lowercase(),
                    displayName = account.displayName,
                    role = account.role,
                    active = true,
                    passwordHash = passwordHasher.hash(account.rawPassword),
                    firebaseUid = null,
                    authSource = AuthSource.LOCAL,
                ),
            )
        }
        return BootstrapResult.Seeded(accounts.size)
    }
}

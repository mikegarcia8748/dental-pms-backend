package com.pms.dental.config

import com.pms.dental.Env
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.usecase.BootstrapAccount
import java.time.Duration

/**
 * Authentication settings read from the environment. Fails fast at construction if the JWT
 * secret is missing or too short, so a misconfigured deployment never boots with a weak key.
 */
data class AuthConfig(
    val jwtSecret: String,
    val jwtIssuer: String,
    val jwtAudience: String,
    val accessTtlMinutes: Long,
    val refreshTtlDays: Long,
    val bootstrapAccounts: List<BootstrapAccount>,
) {
    val accessTtlSeconds: Long get() = accessTtlMinutes * 60
    val refreshTtl: Duration get() = Duration.ofDays(refreshTtlDays)

    companion object {
        private const val MIN_SECRET_LENGTH = 32

        fun fromEnv(): AuthConfig {
            val secret = Env["JWT_SECRET"]
            require(secret != null && secret.length >= MIN_SECRET_LENGTH) {
                "JWT_SECRET must be set and at least $MIN_SECRET_LENGTH characters long"
            }
            return AuthConfig(
                jwtSecret = secret,
                jwtIssuer = Env.get("JWT_ISSUER", "dental-pms"),
                jwtAudience = Env.get("JWT_AUDIENCE", "dental-pms-web"),
                accessTtlMinutes = Env.int("ACCESS_TOKEN_TTL_MINUTES", 15).toLong(),
                refreshTtlDays = Env.int("REFRESH_TOKEN_TTL_DAYS", 14).toLong(),
                bootstrapAccounts = bootstrapAccountsFromEnv(),
            )
        }

        /** Builds the first-run seed list, skipping any role whose email/password is unset. */
        private fun bootstrapAccountsFromEnv(): List<BootstrapAccount> = buildList {
            account(
                Env["BOOTSTRAP_SYSADMIN_EMAIL"], Env["BOOTSTRAP_SYSADMIN_PASSWORD"],
                Env["BOOTSTRAP_SYSADMIN_NAME"], Role.SYSADMIN,
            )?.let(::add)
            account(
                Env["BOOTSTRAP_DENTIST_EMAIL"], Env["BOOTSTRAP_DENTIST_PASSWORD"],
                Env["BOOTSTRAP_DENTIST_NAME"], Role.DENTIST,
            )?.let(::add)
        }

        private fun account(email: String?, password: String?, name: String?, role: Role): BootstrapAccount? =
            if (email.isNullOrBlank() || password.isNullOrBlank()) null
            else BootstrapAccount(email, password, name?.takeIf { it.isNotBlank() } ?: role.name, role)
    }
}

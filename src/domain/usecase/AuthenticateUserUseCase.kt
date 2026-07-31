package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.AuthTokens
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.PasswordHasher
import com.pms.dental.domain.service.RefreshTokenFactory
import java.time.Duration

/** Outcome of a login attempt. Failures are data so the route can map them to HTTP status. */
sealed interface AuthenticationResult {
    data class Success(val user: AppUser, val tokens: AuthTokens) : AuthenticationResult
    data class Rejected(val error: AuthenticationError) : AuthenticationResult
}

enum class AuthenticationError {
    /** Unknown email OR wrong password — deliberately indistinguishable to prevent enumeration. */
    InvalidCredentials,

    /** Credentials were correct but the account is deactivated. */
    InactiveAccount,
}

/**
 * Business rule: authenticate an email + password. On success, issues a fresh access token and
 * a rotated (newly stored) refresh token. Unknown email and wrong password fail identically;
 * a deactivated account is rejected only after the credentials themselves check out.
 */
class AuthenticateUserUseCase(
    private val users: AppUserRepository,
    private val refreshTokens: RefreshTokenRepository,
    private val passwordHasher: PasswordHasher,
    private val accessTokenIssuer: AccessTokenIssuer,
    private val refreshTokenFactory: RefreshTokenFactory,
    private val clock: Clock,
    private val refreshTokenTtl: Duration,
) {
    /**
     * A throwaway hash computed with the injected hasher (so it always matches production's bcrypt
     * cost). We verify against it when the email is unknown, so an unknown email costs the same
     * bcrypt work as a wrong password and can't be distinguished by response time. Computed lazily
     * to keep construction cheap and avoid running bcrypt against a mocked hasher during tests.
     */
    private val dummyHash: String by lazy { passwordHasher.hash(DUMMY_PASSWORD) }

    suspend operator fun invoke(email: String, rawPassword: String): AuthenticationResult {
        val user = users.findByEmail(email.trim().lowercase())
        // Only LOCAL accounts carry a password. A Firebase account (null hash), like an unknown
        // email, must never authenticate via this path — and must cost the same bcrypt work, so it
        // can't be told apart by response time.
        val localHash = user?.takeIf { it.authSource == AuthSource.LOCAL }?.passwordHash
        if (user == null || localHash == null) {
            passwordHasher.verify(rawPassword, dummyHash) // equalize timing; result deliberately ignored
            return AuthenticationResult.Rejected(AuthenticationError.InvalidCredentials)
        }

        if (!passwordHasher.verify(rawPassword, localHash)) {
            return AuthenticationResult.Rejected(AuthenticationError.InvalidCredentials)
        }
        // Only reveal deactivation to someone who already proved the credentials.
        if (!user.active) {
            return AuthenticationResult.Rejected(AuthenticationError.InactiveAccount)
        }

        val access = accessTokenIssuer.issue(user)
        val refresh = refreshTokenFactory.newToken()
        refreshTokens.store(user.id, refresh.hash, clock.now().plus(refreshTokenTtl))

        return AuthenticationResult.Success(
            user,
            AuthTokens(access.token, refresh.raw, access.expiresInSeconds),
        )
    }

    private companion object {
        /** Arbitrary constant fed to the hasher to build [dummyHash]; never a real credential. */
        const val DUMMY_PASSWORD = "timing-equalization-dummy-password"
    }
}

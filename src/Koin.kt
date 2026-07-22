package com.pms.dental

import com.pms.dental.config.AuthConfig
import com.pms.dental.config.DatabaseConfig
import com.pms.dental.data.ExposedAppUserRepository
import com.pms.dental.data.ExposedRefreshTokenRepository
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import com.pms.dental.domain.service.PasswordHasher
import com.pms.dental.domain.service.RefreshTokenFactory
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.BootstrapUsersUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.infra.BcryptPasswordHasher
import com.pms.dental.infra.DatabaseFactory
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.SecureRandomRefreshTokenFactory
import com.pms.dental.infra.SystemClock
import com.pms.dental.infra.UuidGenerator
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/** Application-wide dependency graph. Reads configuration from the environment at startup. */
val appModule = module {
    single { AuthConfig.fromEnv() }
    single { DatabaseConfig.fromEnv() }
    single { DatabaseFactory(get()) }

    single<Clock> { SystemClock() }
    single<IdGenerator> { UuidGenerator() }
    single<PasswordHasher> { BcryptPasswordHasher() }
    single<RefreshTokenFactory> { SecureRandomRefreshTokenFactory() }
    single<AccessTokenIssuer> {
        val config = get<AuthConfig>()
        JwtAccessTokenIssuer(
            secret = config.jwtSecret,
            issuer = config.jwtIssuer,
            audience = config.jwtAudience,
            accessTtlSeconds = config.accessTtlSeconds,
            clock = get(),
        )
    }

    single<AppUserRepository> { ExposedAppUserRepository() }
    single<RefreshTokenRepository> { ExposedRefreshTokenRepository() }

    single {
        AuthenticateUserUseCase(get(), get(), get(), get(), get(), get(), get<AuthConfig>().refreshTtl)
    }
    single {
        RefreshAccessTokenUseCase(get(), get(), get(), get(), get(), get<AuthConfig>().refreshTtl)
    }
    single { LogoutUseCase(get(), get()) }
    single { BootstrapUsersUseCase(get(), get(), get()) }
}

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

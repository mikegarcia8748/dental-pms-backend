package com.pms.dental

import com.pms.dental.admin.StaffUseCases
import com.pms.dental.config.AuthConfig
import com.pms.dental.config.DatabaseConfig
import com.pms.dental.config.FirebaseConfig
import com.pms.dental.data.ExposedAppUserRepository
import com.pms.dental.data.ExposedRefreshTokenRepository
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import com.pms.dental.domain.service.AccessTokenIssuer
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.FirebaseTokenPolicy
import com.pms.dental.domain.service.FirebaseTokenVerifier
import com.pms.dental.domain.service.PolicyCheckingFirebaseTokenVerifier
import com.pms.dental.domain.service.IdGenerator
import com.pms.dental.domain.service.PasswordHasher
import com.pms.dental.domain.service.RefreshTokenFactory
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.BootstrapUsersUseCase
import com.pms.dental.domain.usecase.GetStaffUseCase
import com.pms.dental.domain.usecase.ListStaffUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.OffboardStaffUseCase
import com.pms.dental.domain.usecase.ProvisionStaffUseCase
import com.pms.dental.domain.usecase.ReactivateStaffUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.infra.BcryptPasswordHasher
import com.pms.dental.infra.DatabaseFactory
import com.pms.dental.infra.JwtFirebaseTokenVerifier
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.SecureRandomRefreshTokenFactory
import com.pms.dental.infra.SystemClock
import com.pms.dental.infra.UuidGenerator
import com.pms.dental.data.ExposedAllergyRepository
import com.pms.dental.data.ExposedAuditLogRepository
import com.pms.dental.data.ExposedConsentRepository
import com.pms.dental.data.ExposedConsentTextRepository
import com.pms.dental.data.ExposedIntakeQuestionRepository
import com.pms.dental.data.ExposedPatientIntakeAnswerRepository
import com.pms.dental.data.ExposedPatientRepository
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.ConsentRepository
import com.pms.dental.domain.repository.ConsentTextRepository
import com.pms.dental.domain.repository.IntakeQuestionRepository
import com.pms.dental.domain.repository.PatientIntakeAnswerRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.usecase.AddAllergyUseCase
import com.pms.dental.domain.usecase.DeactivateAllergyUseCase
import com.pms.dental.domain.usecase.DeactivatePatientUseCase
import com.pms.dental.domain.usecase.GetPatientDetailsUseCase
import com.pms.dental.domain.usecase.ListConsentTextsUseCase
import com.pms.dental.domain.usecase.ListIntakeQuestionsUseCase
import com.pms.dental.domain.usecase.ListPatientsUseCase
import com.pms.dental.domain.usecase.ReactivatePatientUseCase
import com.pms.dental.domain.usecase.RecordConsentUseCase
import com.pms.dental.domain.usecase.RegisterPatientUseCase
import com.pms.dental.domain.usecase.UpdateAllergyUseCase
import com.pms.dental.domain.usecase.UpdatePatientUseCase
import com.pms.dental.domain.usecase.UpsertIntakeAnswersUseCase
import com.pms.dental.patient.PatientUseCases
import io.ktor.server.application.Application
import io.ktor.server.application.install
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/** Application-wide dependency graph. Reads configuration from the environment at startup. */
val appModule = module {
    single { AuthConfig.fromEnv() }
    single { DatabaseConfig.fromEnv() }
    single { FirebaseConfig.fromEnv() }
    single { DatabaseFactory(get()) }

    single {
        val config = get<FirebaseConfig>()
        FirebaseTokenPolicy(
            requireVerifiedEmail = config.requireVerifiedEmail,
            allowedSignInProviders = config.allowedSignInProviders,
            maxSessionAge = config.maxSessionAge,
            clock = get(),
        )
    }
    // The policy decorator wraps the JWKS verifier, so every consumer of FirebaseTokenVerifier gets
    // the claim checks — there is no unwrapped binding to accidentally inject. Constructing this
    // contacts nothing: the key set is fetched lazily on the first token.
    single<FirebaseTokenVerifier> {
        PolicyCheckingFirebaseTokenVerifier(
            delegate = JwtFirebaseTokenVerifier(projectId = get<FirebaseConfig>().projectId),
            policy = get(),
        )
    }
    single { AuthenticateFirebaseUserUseCase(get(), get()) }

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

    // Staff administration slice (Firebase-provisioned staff + break-glass management)
    single { ProvisionStaffUseCase(get(), get()) }
    single { ListStaffUseCase(get()) }
    single { GetStaffUseCase(get()) }
    single { OffboardStaffUseCase(get()) }
    single { ReactivateStaffUseCase(get()) }
    single { StaffUseCases(get(), get(), get(), get(), get()) }

    // Patient registration & intake slice
    single<PatientRepository> { ExposedPatientRepository() }
    single<AllergyRepository> { ExposedAllergyRepository() }
    single<IntakeQuestionRepository> { ExposedIntakeQuestionRepository() }
    single<PatientIntakeAnswerRepository> { ExposedPatientIntakeAnswerRepository() }
    single<ConsentRepository> { ExposedConsentRepository() }
    single<ConsentTextRepository> { ExposedConsentTextRepository() }
    single<AuditLogRepository> { ExposedAuditLogRepository() }

    single { RegisterPatientUseCase(get(), get(), get(), get(), get()) }
    single { ListPatientsUseCase(get()) }
    single { GetPatientDetailsUseCase(get(), get(), get(), get()) }
    single { UpdatePatientUseCase(get(), get(), get(), get()) }
    single { DeactivatePatientUseCase(get(), get(), get(), get()) }
    single { ReactivatePatientUseCase(get(), get(), get(), get()) }
    single { AddAllergyUseCase(get(), get(), get(), get(), get()) }
    single { UpdateAllergyUseCase(get(), get(), get(), get()) }
    single { DeactivateAllergyUseCase(get(), get(), get(), get()) }
    single { UpsertIntakeAnswersUseCase(get(), get(), get(), get(), get(), get()) }
    single { RecordConsentUseCase(get(), get(), get(), get(), get(), get()) }
    single { ListIntakeQuestionsUseCase(get()) }
    single { ListConsentTextsUseCase(get()) }
    single {
        PatientUseCases(
            get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(), get(),
        )
    }
}

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

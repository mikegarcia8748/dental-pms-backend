package com.pms.dental

import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import com.pms.dental.config.FirebaseConfig
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.koin.ktor.ext.inject

fun Application.rootModule() {
    configureKoin()
    val authConfig by inject<AuthConfig>()
    val firebaseConfig by inject<FirebaseConfig>()
    val appUsers by inject<AppUserRepository>()
    val firebaseSignIn by inject<AuthenticateFirebaseUserUseCase>()

    // Say once, at boot, which Firebase controls are actually in force — a relaxed control is
    // otherwise invisible, and an unconfigured Firebase silently leaves break-glass as the only way in.
    firebaseConfig.logStartup(log)

    configureOpenApi()
    configureCors()
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity(authConfig, appUsers, firebaseSignIn)
    configureRouting()
}

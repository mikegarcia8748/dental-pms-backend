package com.pms.dental

import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import io.ktor.server.application.Application
import org.koin.ktor.ext.inject

fun Application.rootModule() {
    configureKoin()
    val authConfig by inject<AuthConfig>()

    configureOpenApi()
    configureMonitoring()
    configureSerialization()
    configureStatusPages()
    configureDatabase()
    configureSecurity(authConfig)
    configureRouting()
}

package com.pms.dental

import io.ktor.server.application.Application

fun Application.rootModule() {
    configureKoin()
    configureExposed()
    configurePostgres()
    configureHttp()
    configureMonitoring()
    configureSerialization()
    configureSecurity()
    configureRouting()
}

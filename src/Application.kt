package com.pms.dental

import io.ktor.server.application.Application

fun Application.rootModule() {
    configureKoin()
    configureHttp()
    configureMonitoring()
    configureSerialization()
    configureRouting()
}

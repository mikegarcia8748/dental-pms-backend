package com.pms.dental

import io.ktor.server.application.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

/** Application-wide dependency graph. Services are registered here as slices land. */
val appModule = module {
}

fun Application.configureKoin() {
    install(Koin) {
        slf4jLogger()
        modules(appModule)
    }
}

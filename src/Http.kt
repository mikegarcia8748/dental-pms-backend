package com.pms.dental

import io.ktor.server.application.*
import io.ktor.server.plugins.swagger.*
import io.ktor.server.routing.*

fun Application.configureHttp() {
    routing {
        swaggerUI(path = "openapi")
    }
}

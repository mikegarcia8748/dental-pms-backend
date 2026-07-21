package com.pms.dental

import io.ktor.server.engine.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    embeddedServer(
        factory = io.ktor.server.netty.Netty,
        port = ServerConfig.port,
        host = ServerConfig.host,
        module = Application::rootModule
    ).start(wait = true)
}

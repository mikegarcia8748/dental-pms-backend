package com.pms.dental

import com.pms.dental.auth.authRoutes
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val login by inject<AuthenticateUserUseCase>()
    val refresh by inject<RefreshAccessTokenUseCase>()
    val logout by inject<LogoutUseCase>()

    routing {
        get("/health", {
            summary = "Liveness probe"
            description = "Returns 200 while the server is up."
            tags("System")
            response {
                code(HttpStatusCode.OK) {
                    description = "The service is up."
                    body<Map<String, String>>()
                }
            }
        }) {
            call.respond(mapOf("status" to "ok"))
        }
        authRoutes(login, refresh, logout)
    }
}

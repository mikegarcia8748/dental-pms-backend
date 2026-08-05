package com.pms.dental

import com.pms.dental.admin.StaffUseCases
import com.pms.dental.admin.staffRoutes
import com.pms.dental.auth.authRoutes
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.patient.PatientUseCases
import com.pms.dental.patient.patientRoutes
import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.routing
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val login by inject<AuthenticateUserUseCase>()
    val refresh by inject<RefreshAccessTokenUseCase>()
    val logout by inject<LogoutUseCase>()
    val patientUseCases by inject<PatientUseCases>()
    val staffUseCases by inject<StaffUseCases>()

    routing {
        systemRoutes()
        authRoutes(login, refresh, logout)
        patientRoutes(patientUseCases)
        staffRoutes(staffUseCases)
    }
}

/**
 * Dependency-free "is this thing on?" routes: a service index at `/` and a liveness probe at `/health`.
 * Split out from [configureRouting] so they can be exercised without standing up the Koin graph, and so
 * hitting the base URL returns something useful instead of the bare 404 that an unmapped `/` produces.
 */
fun Route.systemRoutes() {
    // Human landing page. Hidden from the OpenAPI spec: the generator would otherwise emit the root
    // route under an invalid empty ("") path key, and a "/" index is not a meaningful contract entry.
    get("/", { hidden = true }) {
        call.respond(
            mapOf(
                "service" to "Dental PMS API",
                "version" to "0.1.0",
                "health" to "/health",
                "docs" to "/swagger",
                "openapi" to "/api.json",
            ),
        )
    }

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
}

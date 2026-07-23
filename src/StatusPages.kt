package com.pms.dental

import com.pms.dental.auth.ErrorResponse
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("com.pms.dental.StatusPages")

/**
 * Turns unmapped paths and uncaught exceptions into the same JSON [ErrorResponse] shape the rest of the
 * API uses, so clients get `{"error":...}` instead of Ktor's plain-text "page not found" or a leaked
 * stack trace. Install after ContentNegotiation so the error bodies can be serialized.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(status, ErrorResponse("not_found", "No route for ${call.request.path()}"))
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled exception for ${call.request.path()}", cause)
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("internal_error", "Unexpected error"))
        }
    }
}

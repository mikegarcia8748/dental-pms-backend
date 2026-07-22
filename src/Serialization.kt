package com.pms.dental

import io.ktor.server.application.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import kotlinx.serialization.json.Json

/**
 * The single [Json] used for the wire (ContentNegotiation) and for OpenAPI schema generation, so the
 * generated schemas match exactly what the API serializes. See [configureOpenApi].
 */
val appJson = Json

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(appJson)
    }
}

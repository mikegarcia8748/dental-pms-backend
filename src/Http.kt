package com.pms.dental

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.server.application.*
import io.ktor.server.routing.*

/**
 * Installs the OpenAPI plugin (code-first: the spec is generated from the documented routes) and exposes:
 *  - the generated spec at `/api.json`
 *  - Swagger UI at `/swagger`
 *
 * Must run before [configureRouting] so the plugin is present when the documented routes register.
 */
fun Application.configureOpenApi() {
    install(OpenApi) {
        info {
            title = "Dental PMS API"
            version = "0.1.0"
            description = "Dental clinic practice management system API"
        }
        server {
            url = "http://localhost:8080"
            description = "Local development server"
        }
        security {
            // The JWT bearer scheme used by the "Authorize" button in Swagger UI.
            securityScheme("bearerAuth") {
                type = AuthType.HTTP
                scheme = AuthScheme.BEARER
                bearerFormat = "JWT"
            }
            // Routes inside an `authenticate { }` block default to this scheme unless they set their own.
            defaultSecuritySchemeNames("bearerAuth")
        }
        schemas {
            // Describe @Serializable DTOs via kotlinx-serialization, using the same Json as the wire.
            generator = SchemaGenerator.kotlinx(appJson)
        }
    }

    routing {
        route("api.json") {
            openApi()
        }
        route("swagger") {
            swaggerUI("/api.json")
        }
    }
}

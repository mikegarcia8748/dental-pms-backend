package com.pms.dental

import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.github.smiley4.ktoropenapi.config.AuthScheme
import io.github.smiley4.ktoropenapi.config.AuthType
import io.github.smiley4.ktoropenapi.config.SchemaGenerator
import io.github.smiley4.ktorswaggerui.swaggerUI
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.*
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.*

/**
 * Enables cross-origin requests from the browser frontend. Without this the server sends no
 * `Access-Control-Allow-Origin` header, so a page served from a different origin (e.g. the dev
 * frontend on :8080 calling the API on :8081) fails its CORS preflight and the fetch is blocked.
 *
 * Installed before routing so the plugin's preflight (`OPTIONS`) handling is in place for every route.
 */
fun Application.configureCors() {
    install(CORS) {
        CorsConfig.allowedHosts.forEach { host ->
            // Allow both http and https so the same config works behind a TLS-terminating proxy.
            allowHost(host, schemes = listOf("http", "https"))
        }

        // Methods used by the auth routes plus the preflight's own OPTIONS.
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)

        // Headers the browser is allowed to send. Authorization carries the JWT bearer token;
        // ContentType is required for JSON request bodies.
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)

        // Off: auth is bearer-token in the Authorization header, not cookies, so credentialed
        // requests aren't used. Flip to true only if you move to cookie-based sessions — and even
        // then keep the explicit origin list above, since the browser forbids credentials + wildcard.
        allowCredentials = false
    }
}

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
            // Relative URL: Swagger UI resolves it against the origin that served the page, so
            // "Try it out" targets whatever host/port is actually in use (8080, 8081, a LAN IP, a proxy)
            // instead of a hardcoded one that drifts out of sync with PORT.
            url = "/"
            description = "This server"
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

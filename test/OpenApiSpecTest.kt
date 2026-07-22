package com.pms.dental

import com.pms.dental.auth.authRoutes
import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk

/**
 * Smoke test for the code-first OpenAPI generation: boot the app with the documented routes and assert
 * the served spec actually contains them. The use cases are relaxed mocks because we only exercise spec
 * generation (`GET /api.json`), never the handlers.
 */
class OpenApiSpecTest : FunSpec({

    val config = AuthConfig(
        jwtSecret = "test-secret-that-is-at-least-32-characters!",
        jwtIssuer = "dental-pms",
        jwtAudience = "dental-pms-web",
        accessTtlMinutes = 15,
        refreshTtlDays = 14,
        bootstrapAccounts = emptyList(),
    )

    test("the generated spec documents the auth endpoints, DTO schemas, and the bearer scheme") {
        testApplication {
            application {
                configureSerialization()
                configureSecurity(config)
                configureOpenApi()
                routing {
                    authRoutes(mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))
                }
            }

            val response = client.get("/api.json")

            response.status shouldBe HttpStatusCode.OK
            val spec = response.bodyAsText()
            spec shouldContain "/auth/login"
            spec shouldContain "/auth/me"
            spec shouldContain "LoginResponse"
            spec shouldContain "bearerAuth"
        }
    }
})

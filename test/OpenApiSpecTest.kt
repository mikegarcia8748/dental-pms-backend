package com.pms.dental

import com.pms.dental.admin.staffRoutes
import com.pms.dental.auth.authRoutes
import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import com.pms.dental.patient.patientRoutes
import com.pms.dental.support.FakeAppUserRepository
import com.pms.dental.support.FakeFirebaseTokenVerifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.mockk

/** Auth is only installed so the documented routes can mount; no request is ever authenticated here. */
private fun firebaseSignIn() =
    AuthenticateFirebaseUserUseCase(FakeFirebaseTokenVerifier(), FakeAppUserRepository())

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
                configureSecurity(config, FakeAppUserRepository(), firebaseSignIn())
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

    test("the generated spec documents the patient endpoints and DTO schemas") {
        testApplication {
            application {
                configureSerialization()
                configureSecurity(config, FakeAppUserRepository(), firebaseSignIn())
                configureOpenApi()
                routing {
                    patientRoutes(mockk(relaxed = true))
                }
            }

            val spec = client.get("/api.json").bodyAsText()

            spec shouldContain "/patients"
            spec shouldContain "/intake-questions"
            spec shouldContain "/consent-texts"
            spec shouldContain "PatientDetailsResponse"
        }
    }

    test("the generated spec documents the staff admin endpoints and DTO schemas") {
        testApplication {
            application {
                configureSerialization()
                configureSecurity(config, FakeAppUserRepository(), firebaseSignIn())
                configureOpenApi()
                routing {
                    staffRoutes(mockk(relaxed = true))
                }
            }

            val spec = client.get("/api.json").bodyAsText()

            spec shouldContain "/admin/staff"
            spec shouldContain "StaffResponse"
        }
    }
})

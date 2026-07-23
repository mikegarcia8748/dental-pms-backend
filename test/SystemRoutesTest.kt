package com.pms.dental

import com.pms.dental.auth.ErrorResponse
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication

/**
 * Covers the dependency-free system routes and the StatusPages fallback: the base URL is a useful
 * index (not a 404), and an unmapped path returns a structured [ErrorResponse] instead of plain text.
 */
class SystemRoutesTest : FunSpec({

    fun io.ktor.server.testing.ApplicationTestBuilder.installSystemApp() {
        application {
            configureSerialization()
            configureStatusPages()
            routing { systemRoutes() }
        }
    }

    fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient() =
        createClient { install(ClientContentNegotiation) { json() } }

    test("root - returns 200 with a service index pointing at the docs") {
        testApplication {
            installSystemApp()
            val client = jsonClient()

            val response = client.get("/")

            response.status shouldBe HttpStatusCode.OK
            response.body<Map<String, String>>()["docs"] shouldBe "/swagger"
        }
    }

    test("unmapped path - returns 404 with a structured error body") {
        testApplication {
            installSystemApp()
            val client = jsonClient()

            val response = client.get("/does-not-exist")

            response.status shouldBe HttpStatusCode.NotFound
            response.body<ErrorResponse>().error shouldBe "not_found"
        }
    }
})

package com.pms.dental

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class ServerTest {

    @Test
    fun `health endpoint reports ok`() = testApplication {
        application {
            configureSerialization()
            routing {
                get("/health") { call.respond(mapOf("status" to "ok")) }
            }
        }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }
}

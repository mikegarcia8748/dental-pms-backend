package com.pms.dental

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.*

class ServerTest {

    @Test
    fun `health endpoint reports ok`() = testApplication {
        application {
            rootModule()
        }
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
    }

}

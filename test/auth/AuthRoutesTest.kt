package com.pms.dental.auth

import com.pms.dental.configureSerialization
import com.pms.dental.configureStatusPages
import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.infra.BcryptPasswordHasher
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.SecureRandomRefreshTokenFactory
import com.pms.dental.support.FakeAppUserRepository
import com.pms.dental.support.FakeRefreshTokenRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.response.respond
import io.ktor.server.routing.get as getRoute
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.UUID

private class Wiring {
    val config = AuthConfig(
        jwtSecret = "test-secret-that-is-at-least-32-characters!",
        jwtIssuer = "dental-pms",
        jwtAudience = "dental-pms-web",
        accessTtlMinutes = 15,
        refreshTtlDays = 14,
        bootstrapAccounts = emptyList(),
    )
    val users = FakeAppUserRepository()
    val refreshTokens = FakeRefreshTokenRepository()
    val hasher = BcryptPasswordHasher(cost = 4) // low cost keeps the suite fast
    private val clock = Clock { Instant.now() }
    private val issuer = JwtAccessTokenIssuer(
        config.jwtSecret, config.jwtIssuer, config.jwtAudience, config.accessTtlSeconds, clock,
    )
    private val refreshFactory = SecureRandomRefreshTokenFactory()

    val login = AuthenticateUserUseCase(users, refreshTokens, hasher, issuer, refreshFactory, clock, config.refreshTtl)
    val refresh = RefreshAccessTokenUseCase(users, refreshTokens, issuer, refreshFactory, clock, config.refreshTtl)
    val logout = LogoutUseCase(refreshTokens, refreshFactory)

    fun seedDentist(active: Boolean = true): AppUser {
        val user = AppUser(UUID.randomUUID(), "dentist@clinic.test", "Dr. Molar", Role.DENTIST, active, hasher.hash(PASSWORD))
        users.seed(user)
        return user
    }

    companion object {
        const val PASSWORD = "Secret123!"
    }
}

private fun ApplicationTestBuilder.installAuthApp(w: Wiring) {
    application {
        configureSerialization()
        configureStatusPages()
        configureSecurity(w.config)
        routing {
            authRoutes(w.login, w.refresh, w.logout)
            authorize(Role.SYSADMIN) { getRoute("/admin/probe") { call.respond(mapOf("ok" to "sysadmin")) } }
            authorize(Role.DENTIST) { getRoute("/dentist/probe") { call.respond(mapOf("ok" to "dentist")) } }
        }
    }
}

private fun ApplicationTestBuilder.jsonClient() =
    createClient { install(ClientContentNegotiation) { json() } }

class AuthRoutesTest : FunSpec({

    test("login - valid credentials - returns 200 with tokens and the dentist role") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("dentist@clinic.test", Wiring.PASSWORD))
            }

            response.status shouldBe HttpStatusCode.OK
            val body = response.body<LoginResponse>()
            body.user.role shouldBe "DENTIST"
            body.tokens.accessToken.shouldNotBeBlank()
            body.tokens.refreshToken.shouldNotBeBlank()
        }
    }

    test("login - wrong password - returns 401") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("dentist@clinic.test", "nope"))
            }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("login - deactivated account with correct password - returns 403") {
        val w = Wiring().apply { seedDentist(active = false) }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("dentist@clinic.test", Wiring.PASSWORD))
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("me - with a valid access token returns the user, without a token returns 401") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()
            val tokens = login(client)

            client.get("/auth/me").status shouldBe HttpStatusCode.Unauthorized

            val me = client.get("/auth/me") { bearerAuth(tokens.accessToken) }
            me.status shouldBe HttpStatusCode.OK
            me.body<UserResponse>().role shouldBe "DENTIST"
        }
    }

    test("authorize - a dentist token is forbidden on a sysadmin route but allowed on a dentist route") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()
            val tokens = login(client)

            client.get("/admin/probe") { bearerAuth(tokens.accessToken) }.status shouldBe HttpStatusCode.Forbidden
            client.get("/dentist/probe") { bearerAuth(tokens.accessToken) }.status shouldBe HttpStatusCode.OK
        }
    }

    test("refresh - rotates the token pair, and replaying the old token revokes the whole family") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()
            val original = login(client)

            val refreshed = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(original.refreshToken))
            }
            refreshed.status shouldBe HttpStatusCode.OK
            val rotated = refreshed.body<TokenResponse>()
            rotated.accessToken.shouldNotBeBlank()

            // Replaying the old (now revoked) token is rejected...
            val reuseOld = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(original.refreshToken))
            }
            reuseOld.status shouldBe HttpStatusCode.Unauthorized

            // ...and reuse detection has burned the family, so the freshly-issued token is dead too.
            val useRotated = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(rotated.refreshToken))
            }
            useRotated.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("login - malformed JSON body - returns 400 not 500") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("{ not valid json")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("login - body missing the password field - returns 400") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"dentist@clinic.test"}""")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("login - blank email and password - returns 400") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("", ""))
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("login - password longer than 72 bytes - returns 400") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            val response = client.post("/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest("dentist@clinic.test", "a".repeat(73)))
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("refresh - blank refresh token - returns 400") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            val response = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest("  "))
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("logout - revokes the refresh token so it can no longer be refreshed") {
        val w = Wiring().apply { seedDentist() }
        testApplication {
            installAuthApp(w)
            val client = jsonClient()
            val tokens = login(client)

            val loggedOut = client.post("/auth/logout") {
                contentType(ContentType.Application.Json)
                setBody(LogoutRequest(tokens.refreshToken))
            }
            loggedOut.status shouldBe HttpStatusCode.NoContent

            val afterLogout = client.post("/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody(RefreshRequest(tokens.refreshToken))
            }
            afterLogout.status shouldBe HttpStatusCode.Unauthorized
        }
    }
})

private suspend fun login(client: io.ktor.client.HttpClient): TokenResponse =
    client.post("/auth/login") {
        contentType(ContentType.Application.Json)
        setBody(LoginRequest("dentist@clinic.test", Wiring.PASSWORD))
    }.body<LoginResponse>().tokens

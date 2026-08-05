package com.pms.dental.auth

import com.pms.dental.configureSerialization
import com.pms.dental.configureStatusPages
import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.FirebaseTokenPolicy
import com.pms.dental.domain.service.PolicyCheckingFirebaseTokenVerifier
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.infra.BcryptPasswordHasher
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.SecureRandomRefreshTokenFactory
import com.pms.dental.support.FakeAppUserRepository
import com.pms.dental.support.FakeFirebaseTokenVerifier
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
import java.time.Duration
import java.time.Instant
import java.util.UUID

private class Wiring(
    requireVerifiedEmail: Boolean = true,
    allowedSignInProviders: Set<String> = setOf("password"),
    maxSessionAge: Duration? = Duration.ofHours(12),
) {
    val config = AuthConfig(
        jwtSecret = "test-secret-that-is-at-least-32-characters!",
        jwtIssuer = "dental-pms",
        jwtAudience = "dental-pms-web",
        accessTtlMinutes = 15,
        refreshTtlDays = 14,
        bootstrapAccounts = emptyList(),
    )
    val users = FakeAppUserRepository()

    /** Registers which tokens are *authentic*; the policy below decides which are acceptable. */
    val firebaseTokens = FakeFirebaseTokenVerifier()

    val refreshTokens = FakeRefreshTokenRepository()
    val hasher = BcryptPasswordHasher(cost = 4) // low cost keeps the suite fast
    private val clock = Clock { Instant.now() }

    /**
     * What `configureSecurity` actually gets — the same policy-wrapped verifier Koin builds, behind
     * the same use case, so these route tests exercise the real claim rules and invite binding
     * rather than a bare fake.
     */
    val firebaseSignIn = AuthenticateFirebaseUserUseCase(
        verifier = PolicyCheckingFirebaseTokenVerifier(
            delegate = firebaseTokens,
            policy = FirebaseTokenPolicy(requireVerifiedEmail, allowedSignInProviders, maxSessionAge, clock),
        ),
        users = users,
    )
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

    /** A provisioned, active Firebase staff row — everything except the token's own claims. */
    fun seedFirebaseStaff(uid: String): AppUser {
        val user = AppUser(
            UUID.randomUUID(), "staff-$uid@clinic.test", "Staff", Role.DENTIST, true, null, uid, AuthSource.FIREBASE,
        )
        users.seed(user)
        return user
    }

    companion object {
        const val PASSWORD = "Secret123!"
    }
}

private const val UID = "firebase-uid-policy"
private const val TOKEN = "firebase-id-token-policy"

private fun ApplicationTestBuilder.installAuthApp(w: Wiring) {
    application {
        configureSerialization()
        configureStatusPages()
        configureSecurity(w.config, w.users, w.firebaseSignIn)
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

    test("me - a valid local token stops working the moment the account is deactivated") {
        val w = Wiring()
        val dentist = w.seedDentist()
        testApplication {
            installAuthApp(w)
            val client = jsonClient()
            val tokens = login(client)

            client.get("/auth/me") { bearerAuth(tokens.accessToken) }.status shouldBe HttpStatusCode.OK

            // Neon is the source of truth for `active`, checked every request — so revocation is instant,
            // even though the access token itself is still cryptographically valid.
            w.users.setActive(dentist.id, false)

            client.get("/auth/me") { bearerAuth(tokens.accessToken) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - a valid firebase token for a provisioned active staff returns 200 with the DB role") {
        val w = Wiring()
        val uid = "firebase-uid-1"
        w.users.seed(
            AppUser(UUID.randomUUID(), "staff@clinic.test", "Staff Dentist", Role.DENTIST, true, null, uid, AuthSource.FIREBASE),
        )
        w.firebaseTokens.accept("firebase-id-token", uid)
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            val me = client.get("/auth/me") { bearerAuth("firebase-id-token") }

            me.status shouldBe HttpStatusCode.OK
            me.body<UserResponse>().role shouldBe "DENTIST"
        }
    }

    test("me - a valid firebase token with no matching local account returns 401 (no auto-provision)") {
        val w = Wiring()
        w.firebaseTokens.accept("firebase-id-token", "unknown-uid")
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            client.get("/auth/me") { bearerAuth("firebase-id-token") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - an unrecognized bearer token returns 401") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            client.get("/auth/me") { bearerAuth("not-a-real-token") }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - an authentic firebase token with an unverified email returns 401") {
        val w = Wiring()
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID, emailVerified = false)
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            val response = client.get("/auth/me") { bearerAuth(TOKEN) }

            response.status shouldBe HttpStatusCode.Unauthorized
            // The body must be byte-identical to any other auth failure: a client that could tell a
            // policy rejection from a forged token would be an oracle for probing the rules. It is
            // also load-bearing — `bearer` has no challenge of its own, so this body comes from
            // auth-local purely because `authorize()` registers it first.
            response.body<ErrorResponse>() shouldBe ErrorResponse("unauthorized", "Missing or invalid token")
        }
    }

    test("me - an authentic firebase token with an unverified email is accepted when the rule is off") {
        val w = Wiring(requireVerifiedEmail = false)
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID, emailVerified = false)
        testApplication {
            installAuthApp(w)

            jsonClient().get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.OK
        }
    }

    test("me - a firebase token from a provider outside the allowlist returns 401") {
        val w = Wiring()
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID, signInProvider = "anonymous")
        testApplication {
            installAuthApp(w)

            jsonClient().get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - a firebase token with no sign-in provider claim returns 401 (fails closed)") {
        val w = Wiring()
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID, signInProvider = null)
        testApplication {
            installAuthApp(w)

            jsonClient().get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - a firebase token whose sign-in is older than the max session age returns 401") {
        val w = Wiring(maxSessionAge = Duration.ofHours(12))
        w.seedFirebaseStaff(UID)
        // The ID token itself would still be unexpired here — the client SDK refreshes it hourly.
        // It is auth_time, pinned to the original sign-in, that has aged out.
        w.firebaseTokens.accept(TOKEN, UID, authTime = Instant.now().minus(Duration.ofHours(13)))
        testApplication {
            installAuthApp(w)

            jsonClient().get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("me - a long-lived firebase sign-in is accepted when the max session age is disabled") {
        val w = Wiring(maxSessionAge = null)
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID, authTime = Instant.now().minus(Duration.ofDays(30)))
        testApplication {
            installAuthApp(w)

            jsonClient().get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.OK
        }
    }

    test("me - the local break-glass account still works when every firebase token is rejected") {
        // The whole point of the break-glass path: if a claim rule (or a misconfigured allowlist)
        // locks out every staff account, a sysadmin must still be able to get in and fix it.
        val w = Wiring(allowedSignInProviders = setOf("no-provider-mints-this")).apply { seedDentist() }
        w.seedFirebaseStaff(UID)
        w.firebaseTokens.accept(TOKEN, UID)
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            client.get("/auth/me") { bearerAuth(TOKEN) }.status shouldBe HttpStatusCode.Unauthorized
            client.get("/auth/me") { bearerAuth(login(client).accessToken) }.status shouldBe HttpStatusCode.OK
        }
    }

    test("authorize - a firebase dentist reaches the dentist route but is forbidden on the sysadmin route") {
        val w = Wiring()
        val uid = "firebase-uid-2"
        w.users.seed(
            AppUser(UUID.randomUUID(), "staff2@clinic.test", "Staff Two", Role.DENTIST, true, null, uid, AuthSource.FIREBASE),
        )
        w.firebaseTokens.accept("firebase-token-2", uid)
        testApplication {
            installAuthApp(w)
            val client = jsonClient()

            client.get("/dentist/probe") { bearerAuth("firebase-token-2") }.status shouldBe HttpStatusCode.OK
            client.get("/admin/probe") { bearerAuth("firebase-token-2") }.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("authorize - a request with no token returns 401") {
        testApplication {
            installAuthApp(Wiring())
            val client = jsonClient()

            client.get("/dentist/probe").status shouldBe HttpStatusCode.Unauthorized
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

package com.pms.dental.admin

import com.pms.dental.auth.ErrorResponse
import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import com.pms.dental.configureSerialization
import com.pms.dental.configureStatusPages
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.usecase.GetStaffUseCase
import com.pms.dental.domain.usecase.ListStaffUseCase
import com.pms.dental.domain.usecase.OffboardStaffUseCase
import com.pms.dental.domain.usecase.ProvisionStaffUseCase
import com.pms.dental.domain.usecase.ReactivateStaffUseCase
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.UuidGenerator
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import com.pms.dental.support.FakeAppUserRepository
import com.pms.dental.support.FakeFirebaseTokenVerifier
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
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
    private val clock = Clock { Instant.now() }
    private val issuer = JwtAccessTokenIssuer(config.jwtSecret, config.jwtIssuer, config.jwtAudience, config.accessTtlSeconds, clock)
    private val ids = UuidGenerator()

    val users = FakeAppUserRepository()

    // These tests authenticate as the LOCAL sysadmin; the Firebase path just has to be wired up.
    val firebaseSignIn = AuthenticateFirebaseUserUseCase(FakeFirebaseTokenVerifier(), users)

    val useCases = StaffUseCases(
        provision = ProvisionStaffUseCase(users, ids),
        list = ListStaffUseCase(users),
        get = GetStaffUseCase(users),
        offboard = OffboardStaffUseCase(users),
        reactivate = ReactivateStaffUseCase(users),
    )

    private val sysadmin = AppUser(UUID.randomUUID(), "admin@clinic.test", "Admin", Role.SYSADMIN, true, "hash")
    private val dentist = AppUser(UUID.randomUUID(), "dentist@clinic.test", "Dentist", Role.DENTIST, true, "hash")

    init {
        users.seed(sysadmin)
        users.seed(dentist)
    }

    fun sysadminToken(): String = issuer.issue(sysadmin).token
    fun dentistToken(): String = issuer.issue(dentist).token
}

private fun ApplicationTestBuilder.installApp(w: Wiring) {
    application {
        configureSerialization()
        configureStatusPages()
        configureSecurity(w.config, w.users, w.firebaseSignIn)
        routing { staffRoutes(w.useCases) }
    }
}

private fun ApplicationTestBuilder.jsonClient() =
    createClient { install(ClientContentNegotiation) { json() } }

class StaffRoutesTest : FunSpec({

    test("POST /admin/staff - as sysadmin - invites a Firebase dentist and returns 201 unclaimed") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "Dr. New", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.Created
            val staff = response.body<StaffResponse>()
            staff.role shouldBe "DENTIST"
            staff.authSource shouldBe "FIREBASE"
            staff.active shouldBe true
            // The invite exists but nobody has signed in with Google as this address yet — that is
            // the only state provisioning can produce now that it never touches Firebase.
            staff.signedIn shouldBe false
        }
    }

    test("POST /admin/staff - as dentist - is forbidden with 403") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "Dr. New", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("POST /admin/staff - without a token - returns 401") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "Dr. New", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /admin/staff - unknown role - returns 400") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "Dr. New", "WIZARD"))
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /admin/staff - an email longer than the column - returns 400 rather than a failed insert") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("a".repeat(315) + "@clinic.test", "Dr. New", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorResponse>().error shouldBe "invalid_request"
            // Caught at the boundary: the column is VARCHAR(320), so without the cap this reaches
            // Postgres and comes back as a 500.
            w.users.findByEmail("a".repeat(315) + "@clinic.test") shouldBe null
        }
    }

    test("POST /admin/staff - a displayName longer than the column - returns 400") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "D".repeat(201), "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.BadRequest
            w.users.findByEmail("newdentist@clinic.test") shouldBe null
        }
    }

    test("POST /admin/staff - padded email and displayName - are trimmed before they are stored") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("  NewDentist@Clinic.test  ", "  Dr. New  ", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.Created
            val staff = response.body<StaffResponse>()
            staff.email shouldBe "newdentist@clinic.test"
            staff.displayName shouldBe "Dr. New"
        }
    }

    test("POST /admin/staff - a whitespace-only displayName - returns 400") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("newdentist@clinic.test", "   ", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /admin/staff - an unparseable active filter - returns 400 rather than listing everyone") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.get("/admin/staff?active=maybe") { bearerAuth(w.sysadminToken()) }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorResponse>().error shouldBe "invalid_request"
        }
    }

    test("POST /admin/staff - an email already used by the local admin - returns 409") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("admin@clinic.test", "Clashing", "DENTIST"))
            }

            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("GET /admin/staff - as sysadmin - lists the seeded accounts") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.get("/admin/staff") { bearerAuth(w.sysadminToken()) }

            response.status shouldBe HttpStatusCode.OK
            response.body<List<StaffResponse>>().map { it.email }.toSet() shouldBe
                setOf("admin@clinic.test", "dentist@clinic.test")
        }
    }

    test("GET /admin/staff/{id} - unknown id - returns 404") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            client.get("/admin/staff/${UUID.randomUUID()}") { bearerAuth(w.sysadminToken()) }
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("deactivate then reactivate - flips the account's active flag and returns 204") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            // Provision a staff member to act on.
            val created = client.post("/admin/staff") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(ProvisionStaffRequest("target@clinic.test", "Target", "DENTIST"))
            }.body<StaffResponse>()

            client.post("/admin/staff/${created.id}/deactivate") { bearerAuth(w.sysadminToken()) }
                .status shouldBe HttpStatusCode.NoContent
            client.get("/admin/staff/${created.id}") { bearerAuth(w.sysadminToken()) }
                .body<StaffResponse>().active shouldBe false

            client.post("/admin/staff/${created.id}/reactivate") { bearerAuth(w.sysadminToken()) }
                .status shouldBe HttpStatusCode.NoContent
            client.get("/admin/staff/${created.id}") { bearerAuth(w.sysadminToken()) }
                .body<StaffResponse>().active shouldBe true
        }
    }
})

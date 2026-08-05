package com.pms.dental.admin

import com.pms.dental.auth.ErrorResponse
import com.pms.dental.auth.authorize
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.usecase.GetStaffUseCase
import com.pms.dental.domain.usecase.ListStaffUseCase
import com.pms.dental.domain.usecase.OffboardStaffResult
import com.pms.dental.domain.usecase.OffboardStaffUseCase
import com.pms.dental.domain.usecase.ProvisionStaffResult
import com.pms.dental.domain.usecase.ProvisionStaffUseCase
import com.pms.dental.domain.usecase.ReactivateStaffResult
import com.pms.dental.domain.usecase.ReactivateStaffUseCase
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import java.util.UUID

/** The use cases the staff-admin routes adapt. Bundled like `PatientUseCases` for tidy wiring. */
data class StaffUseCases(
    val provision: ProvisionStaffUseCase,
    val list: ListStaffUseCase,
    val get: GetStaffUseCase,
    val offboard: OffboardStaffUseCase,
    val reactivate: ReactivateStaffUseCase,
)

/**
 * Staff administration endpoints. Thin adapters: parse/validate, call the use case, map its sealed
 * result onto an HTTP status. Every route requires the SysAdmin role (either a Firebase SysAdmin or
 * the LOCAL break-glass account).
 *
 * All of these are pure Neon operations — the backend cannot create or disable Firebase users
 * (that needs service-account credentials it deliberately does not hold), so provisioning writes an
 * invite and offboarding flips `active`, which is what actually gates every request.
 */
fun Route.staffRoutes(uc: StaffUseCases) {
    route("/admin/staff") {
        authorize(Role.SYSADMIN) {
            post({
                summary = "Invite a staff member"
                description = "Create the local account. The staff member claims it by signing in with Google " +
                    "using this exact email; the Firebase UID is bound on that first sign-in."
                tags("Admin")
                securitySchemeNames("bearerAuth")
                request { body<ProvisionStaffRequest>() }
                response {
                    code(HttpStatusCode.Created) { description = "Invited; `signedIn` is false until they sign in with Google."; body<StaffResponse>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid input or unknown role."; body<ErrorResponse>() }
                    code(HttpStatusCode.Conflict) { description = "Email already in use (Firebase or local)."; body<ErrorResponse>() }
                }
            }) {
                val body = call.receive<ProvisionStaffRequest>()
                val error = body.validationError()
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                } else when (val result = uc.provision(body.email, body.displayName, Role.valueOf(body.role))) {
                    is ProvisionStaffResult.Provisioned ->
                        call.respond(HttpStatusCode.Created, result.user.toStaffResponse())
                    ProvisionStaffResult.AlreadyProvisioned ->
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("already_provisioned", "This email is already a Firebase staff account"))
                    ProvisionStaffResult.LocalAccountExists ->
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("local_account_exists", "This email belongs to a local break-glass account"))
                }
            }

            get({
                summary = "List staff"
                description = "All staff accounts. Optional `active` query filter (true/false)."
                tags("Admin")
                securitySchemeNames("bearerAuth")
                response {
                    code(HttpStatusCode.OK) { description = "The staff accounts."; body<List<StaffResponse>>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid query parameter."; body<ErrorResponse>() }
                }
            }) {
                // Reject an unparseable filter rather than silently listing everyone — same rule as
                // `includeInactive` on GET /patients. Absent stays absent (no filter).
                val activeStr = call.request.queryParameters["active"]
                val activeOnly = activeStr?.toBooleanStrictOrNull()
                if (activeStr != null && activeOnly == null) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_request", "active must be true or false"),
                    )
                } else {
                    call.respond(uc.list(activeOnly).map { it.toStaffResponse() })
                }
            }

            route("/{id}") {
                get({
                    summary = "Get a staff member"
                    tags("Admin")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.OK) { description = "The staff account."; body<StaffResponse>() }
                        code(HttpStatusCode.NotFound) { description = "No such staff account."; body<ErrorResponse>() }
                    }
                }) {
                    val id = call.staffId() ?: return@get call.respond(HttpStatusCode.NotFound, notFound())
                    val user = uc.get(id)
                    if (user == null) {
                        call.respond(HttpStatusCode.NotFound, notFound())
                    } else {
                        call.respond(user.toStaffResponse())
                    }
                }

                post("/deactivate", {
                    summary = "Deactivate (offboard) a staff member"
                    description = "Blocks access in Neon immediately, then disables the Firebase user."
                    tags("Admin")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.NoContent) { description = "The account is deactivated." }
                        code(HttpStatusCode.NotFound) { description = "No such staff account."; body<ErrorResponse>() }
                    }
                }) {
                    val id = call.staffId() ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                    when (uc.offboard(id)) {
                        OffboardStaffResult.Offboarded -> call.respond(HttpStatusCode.NoContent)
                        OffboardStaffResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                    }
                }

                post("/reactivate", {
                    summary = "Reactivate a staff member"
                    tags("Admin")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.NoContent) { description = "The account is active again." }
                        code(HttpStatusCode.NotFound) { description = "No such staff account."; body<ErrorResponse>() }
                    }
                }) {
                    val id = call.staffId() ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                    when (uc.reactivate(id)) {
                        ReactivateStaffResult.Reactivated -> call.respond(HttpStatusCode.NoContent)
                        ReactivateStaffResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                    }
                }
            }
        }
    }
}

private fun io.ktor.server.application.ApplicationCall.staffId(): UUID? =
    parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }

private fun notFound() = ErrorResponse("not_found", "No such staff account")

private fun AppUser.toStaffResponse() = StaffResponse(
    id = id.toString(),
    email = email,
    displayName = displayName,
    role = role.name,
    active = active,
    authSource = authSource.name,
    // A bound UID is the proof they have actually signed in with Google at least once. The UID
    // itself stays internal.
    signedIn = firebaseUid != null,
)

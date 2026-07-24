package com.pms.dental.auth

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthTokens
import com.pms.dental.domain.usecase.AuthenticateUserUseCase
import com.pms.dental.domain.usecase.AuthenticationError
import com.pms.dental.domain.usecase.AuthenticationResult
import com.pms.dental.domain.usecase.LogoutUseCase
import com.pms.dental.domain.usecase.RefreshAccessTokenUseCase
import com.pms.dental.domain.usecase.RefreshError
import com.pms.dental.domain.usecase.RefreshResult
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * The authentication endpoints. Each handler is a thin adapter: receive a DTO, call the use case,
 * and map its sealed result onto an HTTP status. All authorization decisions live in the use cases.
 */
fun Route.authRoutes(
    login: AuthenticateUserUseCase,
    refresh: RefreshAccessTokenUseCase,
    logout: LogoutUseCase,
) {
    route("/auth") {
        post("/login", {
            summary = "Log in"
            description = "Exchange email and password for an access token and a refresh token."
            tags("Auth")
            request { body<LoginRequest>() }
            response {
                code(HttpStatusCode.OK) {
                    description = "Authenticated; returns the user and a fresh token pair."
                    body<LoginResponse>()
                }
                code(HttpStatusCode.Unauthorized) {
                    description = "Invalid email or password."
                    body<ErrorResponse>()
                }
                code(HttpStatusCode.Forbidden) {
                    description = "The account is deactivated."
                    body<ErrorResponse>()
                }
            }
        }) {
            val body = call.receive<LoginRequest>()
            val validationError = body.validationError()
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", validationError))
            } else when (val result = login(body.email, body.password)) {
                is AuthenticationResult.Success ->
                    call.respond(HttpStatusCode.OK, result.toLoginResponse())
                is AuthenticationResult.Rejected -> when (result.error) {
                    AuthenticationError.InvalidCredentials ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_credentials", "Invalid email or password"))
                    AuthenticationError.InactiveAccount ->
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("inactive_account", "This account is deactivated"))
                }
            }
        }

        post("/refresh", {
            summary = "Refresh tokens"
            description = "Rotate a valid refresh token for a new token pair. The old refresh token is revoked."
            tags("Auth")
            request { body<RefreshRequest>() }
            response {
                code(HttpStatusCode.OK) {
                    description = "A new token pair; the presented refresh token is now revoked."
                    body<TokenResponse>()
                }
                code(HttpStatusCode.Unauthorized) {
                    description = "The refresh token is invalid, expired, or already used/revoked."
                    body<ErrorResponse>()
                }
                code(HttpStatusCode.Forbidden) {
                    description = "The account is deactivated."
                    body<ErrorResponse>()
                }
            }
        }) {
            val body = call.receive<RefreshRequest>()
            val validationError = body.validationError()
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", validationError))
            } else when (val result = refresh(body.refreshToken)) {
                is RefreshResult.Success ->
                    call.respond(HttpStatusCode.OK, result.tokens.toTokenResponse())
                is RefreshResult.Rejected -> when (result.error) {
                    RefreshError.InactiveAccount ->
                        call.respond(HttpStatusCode.Forbidden, ErrorResponse("inactive_account", "This account is deactivated"))
                    else ->
                        call.respond(HttpStatusCode.Unauthorized, ErrorResponse("invalid_refresh_token", "Refresh token is invalid, expired, or revoked"))
                }
            }
        }

        post("/logout", {
            summary = "Log out"
            description = "Revoke the given refresh token. Idempotent: unknown tokens still return 204."
            tags("Auth")
            request { body<LogoutRequest>() }
            response {
                code(HttpStatusCode.NoContent) {
                    description = "The refresh token has been revoked (if it existed)."
                }
            }
        }) {
            val body = call.receive<LogoutRequest>()
            val validationError = body.validationError()
            if (validationError != null) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", validationError))
            } else {
                logout(body.refreshToken)
                call.respond(HttpStatusCode.NoContent)
            }
        }

        authenticate("auth-jwt") {
            get("/me", {
                summary = "Current user"
                description = "Return the profile of the authenticated user."
                tags("Auth")
                securitySchemeNames("bearerAuth")
                response {
                    code(HttpStatusCode.OK) {
                        description = "The authenticated user's profile."
                        body<UserResponse>()
                    }
                    code(HttpStatusCode.Unauthorized) {
                        description = "Missing or invalid bearer token."
                        body<ErrorResponse>()
                    }
                }
            }) {
                val user = call.authenticatedUser
                if (user == null) {
                    call.respond(HttpStatusCode.Unauthorized, ErrorResponse("unauthorized", "Authentication required"))
                } else {
                    call.respond(user.toUserResponse())
                }
            }
        }
    }
}

private fun AuthenticationResult.Success.toLoginResponse() =
    LoginResponse(user.toUserResponse(), tokens.toTokenResponse())

private fun AuthTokens.toTokenResponse() =
    TokenResponse(accessToken, refreshToken, accessTokenExpiresInSeconds)

private fun AppUser.toUserResponse() =
    UserResponse(id.toString(), email, displayName, role.name)

private fun AuthenticatedUser.toUserResponse() =
    UserResponse(id.toString(), email, displayName, role.name)

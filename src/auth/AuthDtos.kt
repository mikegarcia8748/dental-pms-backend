package com.pms.dental.auth

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(val email: String, val password: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Long,
    val tokenType: String = "Bearer",
)

@Serializable
data class UserResponse(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String,
)

@Serializable
data class LoginResponse(val user: UserResponse, val tokens: TokenResponse)

@Serializable
data class ErrorResponse(val error: String, val message: String)

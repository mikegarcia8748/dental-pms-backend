package com.pms.dental.auth

/**
 * Lightweight request-shape validation for the auth endpoints. These are an HTTP-adapter concern
 * (the use cases stay pure), so each function returns a human-readable message when the input is
 * unacceptable, or `null` when it is fine. Routes turn a non-null result into a 400.
 */

/** Max email length; matches the `app_user.email VARCHAR(320)` column. */
private const val MAX_EMAIL_LENGTH = 320

/**
 * bcrypt only considers the first 72 bytes of a password and silently ignores the rest, so a
 * password longer than that would authenticate on its truncated prefix. Reject it outright.
 */
private const val MAX_PASSWORD_BYTES = 72

/** A refresh token is a 43-char base64url string; anything far larger is not worth hashing. */
private const val MAX_REFRESH_TOKEN_LENGTH = 4096

fun LoginRequest.validationError(): String? = when {
    email.isBlank() -> "email is required"
    email.length > MAX_EMAIL_LENGTH -> "email is too long"
    password.isBlank() -> "password is required"
    password.toByteArray(Charsets.UTF_8).size > MAX_PASSWORD_BYTES ->
        "password must be at most $MAX_PASSWORD_BYTES bytes"
    else -> null
}

fun RefreshRequest.validationError(): String? = refreshToken.refreshTokenError()

fun LogoutRequest.validationError(): String? = refreshToken.refreshTokenError()

private fun String.refreshTokenError(): String? = when {
    isBlank() -> "refreshToken is required"
    length > MAX_REFRESH_TOKEN_LENGTH -> "refreshToken is too long"
    else -> null
}

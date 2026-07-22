package com.pms.dental.domain.service

import com.pms.dental.domain.model.AppUser

/** A signed access token plus how long it is valid, so callers can tell the client. */
data class IssuedAccessToken(val token: String, val expiresInSeconds: Long)

/** Issues a signed access token (JWT) carrying the user's identity and role. */
interface AccessTokenIssuer {
    fun issue(user: AppUser): IssuedAccessToken
}

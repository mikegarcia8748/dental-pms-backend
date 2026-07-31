package com.pms.dental.infra

import com.auth0.jwk.JwkProvider
import com.auth0.jwk.JwkProviderBuilder
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.pms.dental.domain.service.FirebaseTokenVerifier
import com.pms.dental.domain.service.FirebaseVerification
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Verifies Firebase ID tokens as what they actually are — RS256 JWTs signed by Google — against
 * Google's published public keys.
 *
 * This is what lets the backend hold **no service-account credentials at all**: the issuer
 * (`https://securetoken.google.com/<projectId>`) and audience (`<projectId>`) both derive from the
 * project id alone, and the signing keys are public. The Firebase Admin SDK is not involved, so
 * there is no `google-services.json` and no key file to distribute, rotate, or leak.
 *
 * The trade-off is that privileged operations — creating, disabling or looking up Firebase users —
 * are no longer possible from here. Staff onboarding is therefore a Neon-only invite claimed on
 * first sign-in (see `AuthenticateFirebaseUserUseCase`), and revocation is the per-request `active`
 * check against Neon.
 *
 * Like the SDK verifier it replaces, this class is a **dumb claim extractor**: it decides only
 * whether a token is authentic and reports what it says. Which tokens are *acceptable* is
 * [com.pms.dental.domain.service.FirebaseTokenPolicy]'s job.
 *
 * When [projectId] is null or blank, Firebase is unconfigured: every token is rejected and only the
 * LOCAL break-glass path can authenticate.
 */
class JwtFirebaseTokenVerifier(
    private val projectId: String?,
    /**
     * Injectable so tests can serve a locally generated key instead of reaching the network. The
     * default is built **lazily** — resolving the Koin graph must not contact Google.
     */
    jwkProvider: Lazy<JwkProvider> = lazy { googleSecureTokenJwks() },
) : FirebaseTokenVerifier {

    private val log = LoggerFactory.getLogger(javaClass)
    private val jwks by jwkProvider

    private val issuer = "https://securetoken.google.com/$projectId"

    override suspend fun verify(idToken: String): FirebaseVerification {
        if (projectId.isNullOrBlank() || idToken.isBlank()) return FirebaseVerification.Invalid
        return withContext(Dispatchers.IO) {
            try {
                // Read the key id from the unverified header to pick the right public key. Nothing
                // is trusted yet — a wrong or forged kid simply fails the signature check below.
                val keyId = JWT.decode(idToken).keyId
                val publicKey = jwks.get(keyId).publicKey as RSAPublicKey

                val verified = JWT.require(Algorithm.RSA256(publicKey))
                    .withIssuer(issuer)
                    .withAudience(projectId)
                    // Google and Cloud Run clocks drift by seconds; without leeway a token minted
                    // moments ago can look not-yet-valid.
                    .acceptLeeway(CLOCK_SKEW_SECONDS)
                    .build()
                    .verify(idToken)

                // java-jwt checks exp/nbf/iat but not that a subject exists. `sub` is the Firebase
                // UID we join the local account on, so a blank one is unusable.
                val uid = verified.subject
                if (uid.isNullOrBlank()) return@withContext FirebaseVerification.Invalid

                FirebaseVerification.Valid(
                    firebaseUid = uid,
                    email = verified.getClaim("email").asString(),
                    emailVerified = verified.getClaim("email_verified").asBoolean() ?: false,
                    signInProvider = verified.signInProvider(),
                    authTime = verified.authTime(),
                )
            } catch (e: CancellationException) {
                // Never swallow cancellation — that would break structured concurrency.
                throw e
            } catch (e: Exception) {
                // Expired, wrong signature/issuer/audience, unknown key id, malformed token, or the
                // key set being unreachable. All of them mean "this caller has no usable token" —
                // a 401, never a 500. Message only: a routine bad token does not deserve a stack
                // trace, and the token itself must never be logged.
                log.debug("Firebase ID token rejected: {}", e.message)
                FirebaseVerification.Invalid
            }
        }
    }

    companion object {
        private const val CLOCK_SKEW_SECONDS = 60L

        /**
         * Google's signing keys for Firebase ID tokens, in **JWKS** form.
         *
         * Not the widely-cited `robot/v1/metadata/x509/...` URL — that serves X.509 PEM certificates,
         * which [com.auth0.jwk.UrlJwkProvider] cannot parse.
         */
        private const val JWKS_URL =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"

        /**
         * Caching, rate-limited provider. Google rotates these keys roughly daily, so a 6-hour cache
         * keeps steady-state verification free of network I/O while still picking up a rotation
         * well before old tokens expire. The rate limit bounds the damage if a flood of tokens
         * carries unknown key ids.
         */
        fun googleSecureTokenJwks(): JwkProvider =
            JwkProviderBuilder(URI(JWKS_URL).toURL())
                .cached(10, 6, TimeUnit.HOURS)
                .rateLimited(10, 1, TimeUnit.MINUTES)
                .build()
    }
}

/**
 * `sign_in_provider` is nested under the `firebase` claim — e.g. `google.com`. Every step is a
 * safe cast: a token missing or reshaping the claim yields null, which the policy then rejects.
 */
private fun DecodedJWT.signInProvider(): String? =
    getClaim("firebase").asMap()?.get("sign_in_provider") as? String

/**
 * `auth_time` is seconds since the epoch — when the user actually signed in, not when this token
 * was minted. Read through [Number] rather than a concrete type: the JSON parse may hand back an
 * `Integer`, a `Long` or a `BigDecimal` depending on magnitude and parser.
 */
private fun DecodedJWT.authTime(): Instant? =
    (getClaim("auth_time").`as`(Number::class.java))?.toLong()?.let(Instant::ofEpochSecond)

package com.pms.dental.infra

import com.auth0.jwk.Jwk
import com.auth0.jwk.JwkProvider
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.pms.dental.domain.service.FirebaseVerification
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.Date

private const val PROJECT_ID = "dental-pms-test"
private const val KEY_ID = "test-key-1"
private const val ISSUER = "https://securetoken.google.com/$PROJECT_ID"

private fun rsaKeyPair(): KeyPair =
    KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()

/** A [JwkProvider] that serves [publicKey] for any key id — stands in for Google's JWKS endpoint. */
private fun jwkProviderFor(publicKey: RSAPublicKey): JwkProvider {
    val jwk = mockk<Jwk>()
    every { jwk.publicKey } returns publicKey
    return JwkProvider { jwk }
}

/**
 * Builds a token shaped like a real Firebase ID token. Defaults describe a valid Google sign-in;
 * each test overrides only the part it is probing.
 */
private fun signedToken(
    privateKey: RSAPrivateKey,
    publicKey: RSAPublicKey,
    issuer: String = ISSUER,
    audience: String = PROJECT_ID,
    subject: String? = "firebase-uid-1",
    email: String? = "staff@clinic.test",
    emailVerified: Boolean = true,
    signInProvider: String? = "google.com",
    authTime: Instant? = Instant.now().minusSeconds(30),
    expiresAt: Instant = Instant.now().plusSeconds(3600),
): String = JWT.create()
    .withKeyId(KEY_ID)
    .withIssuer(issuer)
    .withAudience(audience)
    .withSubject(subject)
    .withIssuedAt(Date.from(Instant.now().minusSeconds(30)))
    .withExpiresAt(Date.from(expiresAt))
    .apply {
        email?.let { withClaim("email", it) }
        withClaim("email_verified", emailVerified)
        signInProvider?.let { withClaim("firebase", mapOf("sign_in_provider" to it)) }
        authTime?.let { withClaim("auth_time", it.epochSecond) }
    }
    .sign(Algorithm.RSA256(publicKey, privateKey))

class JwtFirebaseTokenVerifierTest : BehaviorSpec({

    val keyPair = rsaKeyPair()
    val publicKey = keyPair.public as RSAPublicKey
    val privateKey = keyPair.private as RSAPrivateKey

    fun verifier(projectId: String? = PROJECT_ID, key: RSAPublicKey = publicKey) =
        JwtFirebaseTokenVerifier(projectId, lazyOf(jwkProviderFor(key)))

    fun token(
        issuer: String = ISSUER,
        audience: String = PROJECT_ID,
        subject: String? = "firebase-uid-1",
        email: String? = "staff@clinic.test",
        emailVerified: Boolean = true,
        signInProvider: String? = "google.com",
        authTime: Instant? = Instant.now().minusSeconds(30),
        expiresAt: Instant = Instant.now().plusSeconds(3600),
    ) = signedToken(privateKey, publicKey, issuer, audience, subject, email, emailVerified, signInProvider, authTime, expiresAt)

    given("a well-formed Firebase ID token") {
        `when`("it is signed by the advertised key for this project") {
            then("every claim the policy needs is carried across") {
                val authTime = Instant.now().minusSeconds(120)

                val result = verifier().verify(token(authTime = authTime))

                val valid = result.shouldBeInstanceOf<FirebaseVerification.Valid>()
                valid.firebaseUid shouldBe "firebase-uid-1"
                valid.email shouldBe "staff@clinic.test"
                valid.emailVerified shouldBe true
                // Nested under the `firebase` claim — the trap this test exists to pin.
                valid.signInProvider shouldBe "google.com"
                valid.authTime shouldBe Instant.ofEpochSecond(authTime.epochSecond)
            }
        }

        `when`("email_verified is false") {
            then("it is still Valid — authenticity and acceptability are different questions") {
                val valid = verifier().verify(token(emailVerified = false))
                    .shouldBeInstanceOf<FirebaseVerification.Valid>()

                valid.emailVerified shouldBe false
            }
        }

        `when`("the optional claims are absent") {
            then("they come back null rather than throwing, so the policy can fail closed") {
                val valid = verifier().verify(token(email = null, signInProvider = null, authTime = null))
                    .shouldBeInstanceOf<FirebaseVerification.Valid>()

                valid.email shouldBe null
                valid.signInProvider shouldBe null
                valid.authTime shouldBe null
            }
        }
    }

    given("a token that should not be trusted") {

        `when`("it is signed by a different key") {
            then("it is Invalid — this is the forgery case") {
                val attacker = rsaKeyPair()
                val forged = signedToken(attacker.private as RSAPrivateKey, attacker.public as RSAPublicKey)

                verifier().verify(forged) shouldBe FirebaseVerification.Invalid
            }
        }

        `when`("the issuer is another Firebase project") {
            then("it is Invalid") {
                verifier().verify(token(issuer = "https://securetoken.google.com/someone-else")) shouldBe
                    FirebaseVerification.Invalid
            }
        }

        `when`("the audience is another Firebase project") {
            then("it is Invalid") {
                verifier().verify(token(audience = "someone-else")) shouldBe FirebaseVerification.Invalid
            }
        }

        `when`("it has expired") {
            then("it is Invalid") {
                verifier().verify(token(expiresAt = Instant.now().minusSeconds(3600))) shouldBe
                    FirebaseVerification.Invalid
            }
        }

        `when`("it carries no subject") {
            then("it is Invalid — the uid is the join key to the local account") {
                verifier().verify(token(subject = null)) shouldBe FirebaseVerification.Invalid
            }
        }

        `when`("it is not a JWT at all") {
            then("it is Invalid rather than throwing — a bad token is a 401, never a 500") {
                verifier().verify("not-a-jwt") shouldBe FirebaseVerification.Invalid
                verifier().verify("") shouldBe FirebaseVerification.Invalid
            }
        }
    }

    given("an unconfigured Firebase") {
        `when`("no project id is set") {
            then("every token is rejected and the key set is never consulted") {
                verifier(projectId = null).verify(token()) shouldBe FirebaseVerification.Invalid
                verifier(projectId = "  ").verify(token()) shouldBe FirebaseVerification.Invalid
            }
        }
    }
})

package com.pms.dental.domain.service

import com.pms.dental.support.FakeFirebaseTokenVerifier
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Duration
import java.time.Instant

private const val TOKEN = "an-authentic-token"

private fun verifier(
    delegate: FakeFirebaseTokenVerifier,
    allowedSignInProviders: Set<String> = setOf("password"),
) = PolicyCheckingFirebaseTokenVerifier(
    delegate,
    FirebaseTokenPolicy(
        requireVerifiedEmail = true,
        allowedSignInProviders = allowedSignInProviders,
        maxSessionAge = Duration.ofHours(12),
        clock = Clock { Instant.now() },
    ),
)

class PolicyCheckingFirebaseTokenVerifierTest : BehaviorSpec({

    given("a token the delegate considers authentic") {
        `when`("it also satisfies the policy") {
            then("it passes through unchanged, claims and all") {
                val delegate = FakeFirebaseTokenVerifier().apply { accept(TOKEN, "uid-1") }

                val result = verifier(delegate).verify(TOKEN)

                result.shouldBeInstanceOf<FirebaseVerification.Valid>().firebaseUid shouldBe "uid-1"
            }
        }

        `when`("the policy rejects it") {
            then("it collapses to Invalid — the caller cannot tell it from a forged token") {
                val delegate = FakeFirebaseTokenVerifier().apply { accept(TOKEN, "uid-1", emailVerified = false) }

                verifier(delegate).verify(TOKEN) shouldBe FirebaseVerification.Invalid
            }
        }
    }

    given("a token the delegate rejects") {
        `when`("the policy would have accepted its claims") {
            then("it is still Invalid — the policy can only narrow, never widen, what is accepted") {
                val delegate = FakeFirebaseTokenVerifier() // nothing registered: everything is Invalid

                verifier(delegate, allowedSignInProviders = setOf("password")).verify(TOKEN) shouldBe
                    FirebaseVerification.Invalid
            }
        }
    }
})

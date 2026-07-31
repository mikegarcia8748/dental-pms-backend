package com.pms.dental.domain.service

import com.pms.dental.support.FIXED_NOW
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration
import java.time.Instant

private fun policy(
    requireVerifiedEmail: Boolean = true,
    allowedSignInProviders: Set<String> = setOf("password"),
    maxSessionAge: Duration? = Duration.ofHours(12),
) = FirebaseTokenPolicy(requireVerifiedEmail, allowedSignInProviders, maxSessionAge, Clock { FIXED_NOW })

/** An otherwise-acceptable token; each test overrides only the claim under examination. */
private fun token(
    emailVerified: Boolean = true,
    signInProvider: String? = "password",
    authTime: Instant? = FIXED_NOW,
) = FirebaseVerification.Valid("uid-1", "staff@clinic.test", emailVerified, signInProvider, authTime)

private fun rejectedFor(reason: FirebasePolicyRejection) = FirebasePolicyDecision.Rejected(reason)

class FirebaseTokenPolicyTest : BehaviorSpec({

    given("an ordinary staff token") {
        `when`("every rule is satisfied") {
            then("it is accepted") {
                policy().evaluate(token()) shouldBe FirebasePolicyDecision.Accepted
            }
        }
    }

    given("the email-verified rule") {
        `when`("the token says the email was never verified") {
            then("it is rejected") {
                policy().evaluate(token(emailVerified = false)) shouldBe
                    rejectedFor(FirebasePolicyRejection.EmailNotVerified)
            }
        }

        `when`("the rule is switched off") {
            then("an unverified email is accepted") {
                policy(requireVerifiedEmail = false).evaluate(token(emailVerified = false)) shouldBe
                    FirebasePolicyDecision.Accepted
            }
        }
    }

    given("the sign-in provider allowlist") {
        `when`("the provider is not on the list") {
            then("it is rejected — an anonymous sign-in must not reach a clinical API") {
                policy().evaluate(token(signInProvider = "anonymous")) shouldBe
                    rejectedFor(FirebasePolicyRejection.UnsupportedProvider)
            }
        }

        `when`("the claim is absent altogether") {
            then("it is rejected: a missing claim is not evidence the rule is satisfied") {
                policy().evaluate(token(signInProvider = null)) shouldBe
                    rejectedFor(FirebasePolicyRejection.UnsupportedProvider)
            }
        }

        `when`("the list is widened to include the provider") {
            then("it is accepted") {
                policy(allowedSignInProviders = setOf("password", "google.com"))
                    .evaluate(token(signInProvider = "google.com")) shouldBe FirebasePolicyDecision.Accepted
            }
        }

        `when`("the provider differs only by case") {
            then("it is rejected — the allowlist matches exactly, as Firebase emits it") {
                policy().evaluate(token(signInProvider = "PASSWORD")) shouldBe
                    rejectedFor(FirebasePolicyRejection.UnsupportedProvider)
            }
        }
    }

    given("the max session age rule") {
        `when`("the sign-in is older than the maximum") {
            then("it is rejected") {
                policy().evaluate(token(authTime = FIXED_NOW.minus(Duration.ofHours(13)))) shouldBe
                    rejectedFor(FirebasePolicyRejection.SessionTooOld)
            }
        }

        `when`("the sign-in is exactly at the maximum") {
            then("it is accepted — the boundary is inclusive") {
                policy().evaluate(token(authTime = FIXED_NOW.minus(Duration.ofHours(12)))) shouldBe
                    FirebasePolicyDecision.Accepted
            }
        }

        `when`("auth_time is slightly in the future") {
            then("it is accepted: clock skew between Firebase and this server is not an attack") {
                policy().evaluate(token(authTime = FIXED_NOW.plusSeconds(30))) shouldBe
                    FirebasePolicyDecision.Accepted
            }
        }

        `when`("auth_time is absent but a maximum is configured") {
            then("it is rejected — nothing to measure means the rule cannot be satisfied") {
                policy().evaluate(token(authTime = null)) shouldBe
                    rejectedFor(FirebasePolicyRejection.MissingAuthTime)
            }
        }

        `when`("the rule is disabled") {
            then("a month-old sign-in is accepted, and a missing auth_time no longer matters") {
                val disabled = policy(maxSessionAge = null)
                disabled.evaluate(token(authTime = FIXED_NOW.minus(Duration.ofDays(30)))) shouldBe
                    FirebasePolicyDecision.Accepted
                disabled.evaluate(token(authTime = null)) shouldBe FirebasePolicyDecision.Accepted
            }
        }
    }

    given("a token that breaks several rules at once") {
        `when`("the email is unverified and the session is stale") {
            then("the email rule is reported — checks run cheapest-first and stop at the first failure") {
                policy().evaluate(
                    token(emailVerified = false, authTime = FIXED_NOW.minus(Duration.ofDays(7))),
                ) shouldBe rejectedFor(FirebasePolicyRejection.EmailNotVerified)
            }
        }
    }
})

package com.pms.dental.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class FirebaseConfigTest : BehaviorSpec({

    fun config(projectId: String? = "dental-pms") = FirebaseConfig(projectId)

    given("resolving whether Firebase is enabled") {

        `when`("a project id is present") {
            then("Firebase is enabled — a project id is all JWKS verification needs") {
                config().enabled shouldBe true
            }
        }

        `when`("the project id is missing or blank") {
            then("Firebase is disabled — degrade to break-glass, not a hard failure") {
                config(projectId = null).enabled shouldBe false
                config(projectId = "   ").enabled shouldBe false
            }
        }

        `when`("nothing is configured") {
            then("construction does not throw") {
                config(projectId = null).enabled shouldBe false
            }
        }
    }

    given("the token policy defaults") {
        `when`("no policy values are supplied") {
            then("email verification is required, only Google sign-in is allowed, sessions cap at 12h") {
                val c = config()
                c.requireVerifiedEmail shouldBe true
                c.allowedSignInProviders shouldBe setOf("google.com")
                c.maxSessionAge shouldBe Duration.ofHours(12)
            }
        }
    }

    given("malformed policy values") {
        // Unlike the project id above — which degrades silently so break-glass survives a Firebase
        // outage — a security control that was set but is unreadable must stop the boot.
        `when`("the provider allowlist is empty") {
            then("construction fails: an empty allowlist would reject every staff token") {
                shouldThrow<IllegalArgumentException> {
                    FirebaseConfig("p", allowedSignInProviders = emptySet())
                }
            }
        }

        `when`("the max session age is negative") {
            then("construction fails") {
                shouldThrow<IllegalArgumentException> {
                    FirebaseConfig("p", maxSessionAge = Duration.ofHours(-1))
                }
            }
        }
    }

    given("a disabled session-age rule") {
        `when`("maxSessionAge is null") {
            then("construction succeeds — null is the documented way to turn the rule off") {
                FirebaseConfig("p", maxSessionAge = null).maxSessionAge shouldBe null
            }
        }
    }
})

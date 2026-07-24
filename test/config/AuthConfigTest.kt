package com.pms.dental.config

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.time.Duration

class AuthConfigTest : BehaviorSpec({

    val validSecret = "test-secret-that-is-at-least-32-characters!"

    fun config(
        jwtSecret: String = validSecret,
        accessTtlMinutes: Long = 15,
        refreshTtlDays: Long = 14,
    ) = AuthConfig(
        jwtSecret = jwtSecret,
        jwtIssuer = "dental-pms",
        jwtAudience = "dental-pms-web",
        accessTtlMinutes = accessTtlMinutes,
        refreshTtlDays = refreshTtlDays,
        bootstrapAccounts = emptyList(),
    )

    given("constructing AuthConfig") {

        `when`("the JWT secret is shorter than 32 characters") {
            then("construction fails fast") {
                shouldThrow<IllegalArgumentException> { config(jwtSecret = "too-short") }
            }
        }

        `when`("the access token TTL is zero") {
            then("construction fails fast") {
                shouldThrow<IllegalArgumentException> { config(accessTtlMinutes = 0) }
            }
        }

        `when`("the access token TTL is negative") {
            then("construction fails fast") {
                shouldThrow<IllegalArgumentException> { config(accessTtlMinutes = -1) }
            }
        }

        `when`("the refresh token TTL is zero") {
            then("construction fails fast") {
                shouldThrow<IllegalArgumentException> { config(refreshTtlDays = 0) }
            }
        }

        `when`("the refresh token TTL is negative") {
            then("construction fails fast") {
                shouldThrow<IllegalArgumentException> { config(refreshTtlDays = -7) }
            }
        }

        `when`("every field is valid") {
            then("it constructs and derives the access TTL and refresh duration") {
                val c = config()
                c.accessTtlSeconds shouldBe 900L
                c.refreshTtl shouldBe Duration.ofDays(14)
            }
        }
    }
})

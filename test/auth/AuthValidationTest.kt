package com.pms.dental.auth

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull

class AuthValidationTest : BehaviorSpec({

    given("validating a login request") {

        `when`("email and password are present and well-formed") {
            then("there is no validation error") {
                LoginRequest("dentist@clinic.test", "Secret123!").validationError().shouldBeNull()
            }
        }

        `when`("the email is blank") {
            then("it is reported") {
                LoginRequest("   ", "Secret123!").validationError().shouldNotBeNull()
            }
        }

        `when`("the password is blank") {
            then("it is reported") {
                LoginRequest("dentist@clinic.test", "").validationError().shouldNotBeNull()
            }
        }

        `when`("the email exceeds 320 characters") {
            then("it is reported") {
                val longEmail = "a".repeat(311) + "@clinic.test" // 323 chars
                LoginRequest(longEmail, "Secret123!").validationError().shouldNotBeNull()
            }
        }

        `when`("the password exceeds 72 bytes") {
            then("it is reported, because bcrypt would silently truncate it") {
                LoginRequest("dentist@clinic.test", "a".repeat(73)).validationError().shouldNotBeNull()
            }
        }

        `when`("the password is exactly 72 bytes") {
            then("it is accepted") {
                LoginRequest("dentist@clinic.test", "a".repeat(72)).validationError().shouldBeNull()
            }
        }
    }

    given("validating a refresh request") {

        `when`("the token is present") {
            then("there is no validation error") {
                RefreshRequest("some-refresh-token").validationError().shouldBeNull()
            }
        }

        `when`("the token is blank") {
            then("it is reported") {
                RefreshRequest("  ").validationError().shouldNotBeNull()
            }
        }

        `when`("the token is absurdly long") {
            then("it is reported") {
                RefreshRequest("a".repeat(5000)).validationError().shouldNotBeNull()
            }
        }
    }

    given("validating a logout request") {

        `when`("the token is blank") {
            then("it is reported") {
                LogoutRequest("").validationError().shouldNotBeNull()
            }
        }

        `when`("the token is present") {
            then("there is no validation error") {
                LogoutRequest("some-refresh-token").validationError().shouldBeNull()
            }
        }
    }
})

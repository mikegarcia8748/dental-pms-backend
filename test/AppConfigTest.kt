package com.pms.dental

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class AppConfigTest : BehaviorSpec({

    given("parsing APP_ENV into an Environment") {

        `when`("the value is prod or production (any case)") {
            then("it resolves to PROD") {
                Environment.from("prod") shouldBe Environment.PROD
                Environment.from("production") shouldBe Environment.PROD
                Environment.from("PROD") shouldBe Environment.PROD
                Environment.from("  prod  ") shouldBe Environment.PROD
            }
        }

        `when`("the value is local (any case)") {
            then("it resolves to LOCAL") {
                Environment.from("local") shouldBe Environment.LOCAL
                Environment.from("LOCAL") shouldBe Environment.LOCAL
            }
        }

        `when`("the value is dev") {
            then("it resolves to DEV") {
                Environment.from("dev") shouldBe Environment.DEV
            }
        }

        `when`("the value is unrecognized, blank, or null") {
            then("it falls back to DEV so prod behavior is never enabled by accident") {
                Environment.from("staging") shouldBe Environment.DEV
                Environment.from("") shouldBe Environment.DEV
                Environment.from("   ") shouldBe Environment.DEV
                Environment.from(null) shouldBe Environment.DEV
            }
        }
    }
})

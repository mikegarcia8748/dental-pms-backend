package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.support.FakeAppUserRepository
import com.pms.dental.support.FakeFirebaseTokenVerifier
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.UUID

private const val TOKEN = "google-id-token"
private const val UID = "firebase-uid-1"
private const val EMAIL = "staff@clinic.test"

private class FirebaseSignInFixture {
    val users = FakeAppUserRepository()
    val tokens = FakeFirebaseTokenVerifier()
    val signIn = AuthenticateFirebaseUserUseCase(tokens, users)

    /** An invite a SysAdmin created: email and role, no Firebase identity yet. */
    fun invite(
        email: String = EMAIL,
        active: Boolean = true,
        firebaseUid: String? = null,
        authSource: AuthSource = AuthSource.FIREBASE,
        passwordHash: String? = null,
    ): AppUser = AppUser(
        UUID.randomUUID(), email, "Dr. New", Role.DENTIST, active, passwordHash, firebaseUid, authSource,
    ).also { users.seed(it) }
}

class AuthenticateFirebaseUserUseCaseTest : BehaviorSpec({

    given("a staff member signing in with Google for the first time") {

        `when`("their verified email matches an unclaimed invite") {
            then("the invite is claimed and the uid is bound for good") {
                val f = FirebaseSignInFixture()
                val invited = f.invite()
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                val user = f.signIn(TOKEN)

                user shouldNotBe null
                user!!.id shouldBe invited.id
                user.firebaseUid shouldBe UID
                // Persisted, not just returned — the next request must find it by uid alone.
                f.users.findByFirebaseUid(UID)?.id shouldBe invited.id
            }
        }

        `when`("the token's email differs only by case or padding") {
            then("it still matches — the stored email is normalized") {
                val f = FirebaseSignInFixture()
                val invited = f.invite()
                f.tokens.accept(TOKEN, UID, email = "  Staff@Clinic.TEST  ")

                f.signIn(TOKEN)?.id shouldBe invited.id
            }
        }

        `when`("no invite exists for that email") {
            then("it is rejected — there is no auto-provisioning") {
                val f = FirebaseSignInFixture()
                f.tokens.accept(TOKEN, UID, email = "stranger@example.com")

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("the token carries no email at all") {
            then("it is rejected — there is nothing to match the invite on") {
                val f = FirebaseSignInFixture()
                f.invite()
                f.tokens.accept(TOKEN, UID, email = null)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("the token's email is unverified") {
            then("the invite is NOT claimed, even though the claim policy is not in play here") {
                // The verifier is a bare fake with no policy, standing in for a deployment that has
                // turned FIREBASE_REQUIRE_VERIFIED_EMAIL off. Relaxing that knob must never let an
                // account assert someone else's address to claim their invite.
                val f = FirebaseSignInFixture()
                f.invite()
                f.tokens.accept(TOKEN, UID, email = EMAIL, emailVerified = false)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("the matching row is a LOCAL break-glass account") {
            then("it is rejected — a break-glass account is never converted to a Firebase identity") {
                val f = FirebaseSignInFixture()
                f.invite(authSource = AuthSource.LOCAL, passwordHash = "hash")
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("the invite was already claimed by a different Google account") {
            then("it is rejected — one account, one Firebase identity") {
                val f = FirebaseSignInFixture()
                f.invite(firebaseUid = "someone-elses-uid")
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("the invite has been deactivated before it was ever claimed") {
            then("it is rejected") {
                val f = FirebaseSignInFixture()
                f.invite(active = false)
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("a concurrent request claims the invite first") {
            then("the loser re-reads by uid and still authenticates if it was the same identity") {
                val f = FirebaseSignInFixture()
                val invited = f.invite()
                f.tokens.accept(TOKEN, UID, email = EMAIL)
                // Model losing the conditional UPDATE, then the winner's row becoming visible.
                f.users.failNextBind = true
                f.users.seed(invited.copy(firebaseUid = UID))

                f.signIn(TOKEN)?.id shouldBe invited.id
            }
        }
    }

    given("a staff member who has already signed in") {

        `when`("their uid is already bound to an active account") {
            then("it resolves by uid without touching the invite path") {
                val f = FirebaseSignInFixture()
                val existing = f.invite(firebaseUid = UID)
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                f.signIn(TOKEN)?.id shouldBe existing.id
            }
        }

        `when`("their account has since been deactivated") {
            then("it is rejected — the per-request active check is the revocation lever") {
                val f = FirebaseSignInFixture()
                f.invite(firebaseUid = UID, active = false)
                f.tokens.accept(TOKEN, UID, email = EMAIL)

                f.signIn(TOKEN).shouldBeNull()
            }
        }

        `when`("their Firebase email later changed to one with no invite") {
            then("they still authenticate — the join is on the immutable uid, not the email") {
                val f = FirebaseSignInFixture()
                val existing = f.invite(firebaseUid = UID)
                f.tokens.accept(TOKEN, UID, email = "new.address@clinic.test")

                f.signIn(TOKEN)?.id shouldBe existing.id
            }
        }
    }

    given("a token the verifier rejects") {
        `when`("it is unknown, forged, or fails the claim policy") {
            then("no lookup happens at all") {
                val f = FirebaseSignInFixture()
                f.invite()

                f.signIn("not-a-real-token").shouldBeNull()
            }
        }
    }
})

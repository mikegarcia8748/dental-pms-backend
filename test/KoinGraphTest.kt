package com.pms.dental

import com.pms.dental.admin.StaffUseCases
import com.pms.dental.domain.service.FirebaseTokenPolicy
import com.pms.dental.domain.service.FirebaseTokenVerifier
import com.pms.dental.domain.service.PolicyCheckingFirebaseTokenVerifier
import com.pms.dental.domain.usecase.AuthenticateFirebaseUserUseCase
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.types.shouldBeInstanceOf
import org.koin.dsl.koinApplication

/**
 * Wiring smoke test: the compiler proves the types line up, but only resolution proves the Koin graph
 * has every binding it needs. Resolving the Firebase and staff-admin singletons touches neither the
 * database nor `AuthConfig`/`DatabaseConfig`, so it runs with no env or DB — and a missing binding
 * would throw here instead of at first request in production.
 *
 * It also pins a property worth keeping: **resolving the graph contacts nothing**. The JWKS key set
 * is fetched lazily on the first token, so constructing the verifier must never reach Google. If
 * this test starts doing network I/O, that laziness has been lost.
 */
class KoinGraphTest : FunSpec({

    test("the Koin graph resolves the Firebase and staff-admin bindings without env or a database") {
        val koin = koinApplication { modules(appModule) }.koin
        try {
            koin.get<FirebaseTokenPolicy>().shouldNotBeNull()
            // The bound verifier must be the policy-wrapped one — an unwrapped binding would skip
            // every claim check in production while every test still passed.
            koin.get<FirebaseTokenVerifier>()
                .shouldBeInstanceOf<PolicyCheckingFirebaseTokenVerifier>()
            koin.get<AuthenticateFirebaseUserUseCase>().shouldNotBeNull()
            koin.get<StaffUseCases>().shouldNotBeNull()
        } finally {
            koin.close()
        }
    }
})

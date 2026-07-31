package com.pms.dental.config

import com.pms.dental.Env
import org.slf4j.Logger
import java.time.Duration

/**
 * Firebase Authentication settings, read from the environment.
 *
 * There are **no credentials here** — deliberately. ID tokens are verified as RS256 JWTs against
 * Google's public keys, so a project id is the whole of what this backend needs to know. No
 * service-account key, no `google-services.json`, nothing to rotate or leak. See
 * [com.pms.dental.infra.JwtFirebaseTokenVerifier].
 *
 * Unlike [AuthConfig] and `CorsConfig`, a missing project id does **not** fail startup, not even in
 * production. Staff sign in through Firebase, but a self-hosted **LOCAL break-glass SYSADMIN** must
 * be able to log in even when Firebase or Google is unreachable. So an unconfigured Firebase simply
 * leaves it [enabled] `= false`: the app boots, Firebase tokens are rejected, and only break-glass
 * authenticates. [logStartup] is responsible for saying so loudly.
 *
 * The **token-policy** knobs below behave differently: an unset value takes its default, but a value
 * that is set yet malformed fails fast (see the `init` block and `fromEnv`). Silently ignoring a
 * typo in `FIREBASE_ALLOWED_SIGN_IN_PROVIDERS` would weaken authentication without anyone noticing.
 */
data class FirebaseConfig(
    /** The Firebase project whose ID tokens are accepted; also the token's `aud` and part of its `iss`. */
    val projectId: String?,
    /** Reject ID tokens whose `email_verified` claim is false. */
    val requireVerifiedEmail: Boolean = true,
    /**
     * Accepted `firebase.sign_in_provider` values. Staff sign in with the "Sign in with Google"
     * button, so `google.com` is the only provider the flow produces. Anything listed here can
     * authenticate to the clinical API, so add to it deliberately.
     */
    val allowedSignInProviders: Set<String> = setOf(DEFAULT_SIGN_IN_PROVIDER),
    /**
     * How long a Firebase *sign-in* stays acceptable, measured from the token's `auth_time`. Null
     * disables the check. Note this is not the ID token's own lifetime: the client SDK refreshes
     * that hourly without re-authenticating, so without this rule a session never ends.
     */
    val maxSessionAge: Duration? = Duration.ofHours(DEFAULT_MAX_SESSION_AGE_HOURS),
) {
    /**
     * True when a project id is configured — the whole of what JWKS verification needs. When false,
     * Firebase auth is disabled and only the LOCAL break-glass path can authenticate.
     */
    val enabled: Boolean
        get() = !projectId.isNullOrBlank()

    init {
        require(allowedSignInProviders.isNotEmpty()) {
            "FIREBASE_ALLOWED_SIGN_IN_PROVIDERS must list at least one provider " +
                "(e.g. \"$DEFAULT_SIGN_IN_PROVIDER\"); an empty allowlist would reject every staff token"
        }
        require(maxSessionAge == null || !maxSessionAge.isNegative) {
            "FIREBASE_MAX_SESSION_AGE_HOURS must not be negative (use 0 to disable the check)"
        }
    }

    /**
     * States the claim policy every staff token is held to, once, at boot. Without this, a control
     * that was relaxed months ago is invisible — you cannot see an absent check in a log.
     */
    fun logStartup(log: Logger) {
        if (!enabled) {
            log.warn(
                "Firebase Authentication is DISABLED: FIREBASE_PROJECT_ID is not set. " +
                    "Staff cannot sign in; only the LOCAL break-glass account works.",
            )
            return
        }
        log.info(
            "Firebase Authentication enabled for project '{}' (JWKS verification, no service account). " +
                "Token policy: requireVerifiedEmail={} allowedSignInProviders={} maxSessionAge={}",
            projectId,
            requireVerifiedEmail,
            allowedSignInProviders,
            maxSessionAge ?: "disabled",
        )
        if (!requireVerifiedEmail) {
            log.warn("FIREBASE_REQUIRE_VERIFIED_EMAIL is off: staff can sign in with an unproven email address.")
        }
        if (maxSessionAge == null) {
            log.warn("FIREBASE_MAX_SESSION_AGE_HOURS is 0: a Firebase sign-in never expires server-side.")
        }
    }

    companion object {
        private const val DEFAULT_SIGN_IN_PROVIDER = "google.com"

        /** One clinic shift: staff re-authenticate at most once a day, not once a fortnight. */
        private const val DEFAULT_MAX_SESSION_AGE_HOURS = 12L

        fun fromEnv(): FirebaseConfig = FirebaseConfig(
            projectId = Env["FIREBASE_PROJECT_ID"],
            requireVerifiedEmail = requireVerifiedEmailFromEnv(),
            allowedSignInProviders = allowedSignInProvidersFromEnv(),
            maxSessionAge = maxSessionAgeFromEnv(),
        )

        /** Defaults to true; only an explicit `false` turns the check off. */
        private fun requireVerifiedEmailFromEnv(): Boolean {
            val raw = Env["FIREBASE_REQUIRE_VERIFIED_EMAIL"] ?: return true
            return raw.trim().toBooleanStrictOrNull()
                ?: throw IllegalArgumentException("FIREBASE_REQUIRE_VERIFIED_EMAIL must be true or false, was \"$raw\"")
        }

        /** Comma-separated, parsed like `CorsConfig.allowedHosts`. */
        private fun allowedSignInProvidersFromEnv(): Set<String> {
            val raw = Env["FIREBASE_ALLOWED_SIGN_IN_PROVIDERS"] ?: return setOf(DEFAULT_SIGN_IN_PROVIDER)
            val providers = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            require(providers.isNotEmpty()) {
                "FIREBASE_ALLOWED_SIGN_IN_PROVIDERS was set but contained no provider names"
            }
            return providers
        }

        /** Hours; `0` disables the check entirely. */
        private fun maxSessionAgeFromEnv(): Duration? {
            val raw = Env["FIREBASE_MAX_SESSION_AGE_HOURS"] ?: return Duration.ofHours(DEFAULT_MAX_SESSION_AGE_HOURS)
            val hours = raw.trim().toLongOrNull()
                ?: throw IllegalArgumentException("FIREBASE_MAX_SESSION_AGE_HOURS must be a whole number of hours, was \"$raw\"")
            require(hours >= 0) { "FIREBASE_MAX_SESSION_AGE_HOURS must not be negative (use 0 to disable the check)" }
            return if (hours == 0L) null else Duration.ofHours(hours)
        }
    }
}

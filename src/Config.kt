package com.pms.dental

import java.io.File

/**
 * Configuration lookup. Real environment variables win; a `.env` file in the working
 * directory fills in the rest, which keeps `./kotlin run` convenient during development.
 * Containers pass real environment variables, so the file is simply absent there.
 */
object Env {

    private val dotenv: Map<String, String> by lazy {
        val file = File(System.getProperty("dotenv.file") ?: ".env")
        if (!file.isFile) emptyMap() else file.readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val key = line.substringBefore('=').removePrefix("export ").trim()
                val value = line.substringAfter('=').trim().removeSurrounding("\"").removeSurrounding("'")
                key to value
            }
    }

    operator fun get(key: String): String? =
        System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]?.takeIf { it.isNotBlank() }

    fun get(key: String, default: String): String = get(key) ?: default

    fun int(key: String, default: Int): Int = get(key)?.toIntOrNull() ?: default
}

/**
 * Where the HTTP server binds. `0.0.0.0` makes it reachable from other machines on the LAN.
 * Deliberately not named `HOST`: some shells export that as the machine's hostname, which
 * would silently change the bind address of a local run.
 */
object ServerConfig {
    val host: String get() = Env.get("SERVER_HOST", "0.0.0.0")
    val port: Int get() = Env.int("PORT", 8080)
}

/**
 * Deployment profile. `APP_ENV=prod` (or `production`) turns on production-only guards; any other
 * value — including the unset default — is treated as development.
 */
object AppConfig {
    val environment: String get() = Env.get("APP_ENV", "dev")
    val isProduction: Boolean
        get() = environment.equals("prod", ignoreCase = true) || environment.equals("production", ignoreCase = true)
}

/**
 * Browser origins allowed to call the API cross-origin. Comma-separated `host:port` entries
 * (no scheme — Ktor's `allowHost` takes the authority only).
 *
 * In development, an unset value falls back to a dev frontend reachable as both `localhost` and
 * `127.0.0.1` (the browser treats those as distinct origins). In production the fallback is a
 * misconfiguration, so an unset `CORS_ALLOWED_HOSTS` fails fast rather than booting with a
 * localhost allowlist no real browser will match.
 */
object CorsConfig {
    private val DEV_DEFAULT_HOSTS = listOf("localhost:8080", "127.0.0.1:8080")

    val allowedHosts: List<String>
        get() {
            // Env[...] returns null for an unset OR blank value, so both collapse to the same branch.
            val raw = Env["CORS_ALLOWED_HOSTS"]
            if (raw == null) {
                require(!AppConfig.isProduction) {
                    "CORS_ALLOWED_HOSTS must be set in production (APP_ENV=prod); " +
                        "refusing to fall back to the localhost dev allowlist"
                }
                return DEV_DEFAULT_HOSTS
            }
            val hosts = raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            require(hosts.isNotEmpty()) {
                "CORS_ALLOWED_HOSTS was set but contained no valid host:port entries"
            }
            return hosts
        }
}

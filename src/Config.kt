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

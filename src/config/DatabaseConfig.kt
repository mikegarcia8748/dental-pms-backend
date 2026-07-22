package com.pms.dental.config

import com.pms.dental.Env

/** Connection settings for the Postgres database, read from the environment. */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val poolSize: Int,
) {
    companion object {
        fun fromEnv(): DatabaseConfig = DatabaseConfig(
            url = Env["DATABASE_URL"] ?: error("DATABASE_URL is required"),
            user = Env["DATABASE_USER"] ?: error("DATABASE_USER is required"),
            password = Env["DATABASE_PASSWORD"] ?: error("DATABASE_PASSWORD is required"),
            poolSize = Env.int("DATABASE_POOL_SIZE", 5),
        )
    }
}

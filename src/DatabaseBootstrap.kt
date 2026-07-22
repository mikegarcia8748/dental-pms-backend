package com.pms.dental

import com.pms.dental.config.AuthConfig
import com.pms.dental.domain.usecase.BootstrapResult
import com.pms.dental.domain.usecase.BootstrapUsersUseCase
import com.pms.dental.infra.DatabaseFactory
import io.ktor.server.application.Application
import io.ktor.server.application.log
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject

/**
 * Connects the database (pool + Flyway migrations) and seeds first-run accounts, once at startup.
 * Kept separate from the rest of the wiring so tests can assemble routes without a real database.
 */
fun Application.configureDatabase() {
    val databaseFactory by inject<DatabaseFactory>()
    val bootstrapUsers by inject<BootstrapUsersUseCase>()
    val authConfig by inject<AuthConfig>()

    databaseFactory.connect()

    when (val result = runBlocking { bootstrapUsers(authConfig.bootstrapAccounts) }) {
        is BootstrapResult.Seeded -> log.info("Bootstrap: seeded ${result.createdCount} account(s).")
        BootstrapResult.Skipped -> log.info("Bootstrap: skipped (accounts already exist or none configured).")
    }
}

package com.pms.dental.infra

import com.pms.dental.config.DatabaseConfig
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import javax.sql.DataSource

/**
 * Owns the connection pool and schema lifecycle. [connect] builds a HikariCP pool, runs the
 * Flyway migrations (schema is owned by SQL under `resources/db/migration`, not Exposed's
 * `SchemaUtils`), and hands back the Exposed [Database] handle used by the repositories.
 */
class DatabaseFactory(private val config: DatabaseConfig) {

    fun connect(): Database {
        val dataSource = HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = config.url
                username = config.user
                password = config.password
                maximumPoolSize = config.poolSize
                driverClassName = "org.postgresql.Driver"
                isAutoCommit = false
            },
        )
        migrate(dataSource)
        return Database.connect(dataSource)
    }

    private fun migrate(dataSource: DataSource) {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate()
    }
}

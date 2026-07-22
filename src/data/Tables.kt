@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.uuid.ExperimentalUuidApi

/** Schema is owned by Flyway (`resources/db/migration`); these mirror it for type-safe queries. */

object AppUsers : Table("app_user") {
    val id = uuid("id")
    val email = varchar("email", 320)
    val displayName = varchar("display_name", 200)
    val role = varchar("role", 20)
    val active = bool("active")
    val passwordHash = varchar("password_hash", 100)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_token") {
    val id = uuid("id")
    val userId = uuid("user_id").references(AppUsers.id)
    val tokenHash = varchar("token_hash", 64)
    val expiresAt = timestamp("expires_at")
    val revoked = bool("revoked")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

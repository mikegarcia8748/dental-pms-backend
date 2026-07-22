@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class ExposedAppUserRepository : AppUserRepository {

    override suspend fun findByEmail(email: String): AppUser? = dbQuery {
        AppUsers.selectAll().where { AppUsers.email eq email }.singleOrNull()?.toAppUser()
    }

    override suspend fun findById(id: UUID): AppUser? = dbQuery {
        AppUsers.selectAll().where { AppUsers.id eq id.toKotlinUuid() }.singleOrNull()?.toAppUser()
    }

    override suspend fun insert(user: AppUser): Unit = dbQuery {
        AppUsers.insert {
            it[id] = user.id.toKotlinUuid()
            it[email] = user.email
            it[displayName] = user.displayName
            it[role] = user.role.name
            it[active] = user.active
            it[passwordHash] = user.passwordHash
            it[createdAt] = Instant.now()
        }
        Unit
    }

    override suspend fun countAll(): Long = dbQuery {
        AppUsers.selectAll().count()
    }

    private fun ResultRow.toAppUser() = AppUser(
        id = this[AppUsers.id].toJavaUuid(),
        email = this[AppUsers.email],
        displayName = this[AppUsers.displayName],
        role = Role.valueOf(this[AppUsers.role]),
        active = this[AppUsers.active],
        passwordHash = this[AppUsers.passwordHash],
    )
}

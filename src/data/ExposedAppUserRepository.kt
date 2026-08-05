@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.AuthSource
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.repository.AppUserRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
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

    override suspend fun findByFirebaseUid(firebaseUid: String): AppUser? = dbQuery {
        AppUsers.selectAll().where { AppUsers.firebaseUid eq firebaseUid }.singleOrNull()?.toAppUser()
    }

    override suspend fun insert(user: AppUser): Unit = dbQuery {
        AppUsers.insert {
            it[id] = user.id.toKotlinUuid()
            it[email] = user.email
            it[displayName] = user.displayName
            it[role] = user.role.name
            it[active] = user.active
            it[passwordHash] = user.passwordHash
            it[firebaseUid] = user.firebaseUid
            it[authSource] = user.authSource.name
            it[createdAt] = Instant.now()
        }
        Unit
    }

    override suspend fun bindFirebaseUid(id: UUID, firebaseUid: String): Boolean = dbQuery {
        // The `firebaseUid.isNull()` predicate is the concurrency guard: the UPDATE matches zero rows
        // if another request already claimed this invite, and `update` returns the row count.
        AppUsers.update({ (AppUsers.id eq id.toKotlinUuid()) and AppUsers.firebaseUid.isNull() }) {
            it[AppUsers.firebaseUid] = firebaseUid
        } == 1
    }

    override suspend fun setActive(id: UUID, active: Boolean): Unit = dbQuery {
        AppUsers.update({ AppUsers.id eq id.toKotlinUuid() }) { it[AppUsers.active] = active }
        Unit
    }

    override suspend fun list(activeOnly: Boolean?): List<AppUser> = dbQuery {
        val rows = if (activeOnly == null) {
            AppUsers.selectAll()
        } else {
            AppUsers.selectAll().where { AppUsers.active eq activeOnly }
        }
        rows.map { it.toAppUser() }
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
        firebaseUid = this[AppUsers.firebaseUid],
        authSource = AuthSource.valueOf(this[AppUsers.authSource]),
    )
}

@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.RefreshTokenRecord
import com.pms.dental.domain.repository.RefreshTokenRepository
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.lessEq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.time.Instant
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class ExposedRefreshTokenRepository : RefreshTokenRepository {

    override suspend fun store(userId: UUID, tokenHash: String, expiresAt: Instant): Unit = dbQuery {
        RefreshTokens.insert {
            it[id] = UUID.randomUUID().toKotlinUuid()
            it[RefreshTokens.userId] = userId.toKotlinUuid()
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[revoked] = false
            it[createdAt] = Instant.now()
        }
        Unit
    }

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = dbQuery {
        RefreshTokens.selectAll().where { RefreshTokens.tokenHash eq tokenHash }.singleOrNull()?.toRecord()
    }

    override suspend fun revoke(tokenHash: String): Unit = dbQuery {
        RefreshTokens.update({ RefreshTokens.tokenHash eq tokenHash }) { it[revoked] = true }
        Unit
    }

    override suspend fun revokeAllForUser(userId: UUID): Unit = dbQuery {
        RefreshTokens.update({ RefreshTokens.userId eq userId.toKotlinUuid() }) { it[revoked] = true }
        Unit
    }

    override suspend fun deleteExpired(now: Instant): Unit = dbQuery {
        RefreshTokens.deleteWhere { expiresAt lessEq now }
        Unit
    }

    private fun ResultRow.toRecord() = RefreshTokenRecord(
        id = this[RefreshTokens.id].toJavaUuid(),
        userId = this[RefreshTokens.userId].toJavaUuid(),
        tokenHash = this[RefreshTokens.tokenHash],
        expiresAt = this[RefreshTokens.expiresAt],
        revoked = this[RefreshTokens.revoked],
    )
}

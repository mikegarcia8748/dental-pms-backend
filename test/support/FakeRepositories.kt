package com.pms.dental.support

import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.RefreshTokenRecord
import com.pms.dental.domain.repository.AppUserRepository
import com.pms.dental.domain.repository.RefreshTokenRepository
import java.time.Instant
import java.util.UUID

/** In-memory user store so route/integration tests run without a database. */
class FakeAppUserRepository : AppUserRepository {
    private val byId = linkedMapOf<UUID, AppUser>()

    fun seed(user: AppUser) { byId[user.id] = user }

    override suspend fun findByEmail(email: String): AppUser? = byId.values.find { it.email == email }
    override suspend fun findById(id: UUID): AppUser? = byId[id]
    override suspend fun findByFirebaseUid(firebaseUid: String): AppUser? =
        byId.values.find { it.firebaseUid == firebaseUid }
    override suspend fun insert(user: AppUser) { byId[user.id] = user }

    /**
     * Mirrors the real conditional UPDATE: only an unclaimed row can be bound, and the boolean says
     * whether *this* call did it. Set [failNextBind] to model losing the race to a concurrent
     * first sign-in without needing real concurrency.
     */
    var failNextBind = false
    override suspend fun bindFirebaseUid(id: UUID, firebaseUid: String): Boolean {
        if (failNextBind) {
            failNextBind = false
            return false
        }
        val user = byId[id] ?: return false
        if (user.firebaseUid != null) return false
        byId[id] = user.copy(firebaseUid = firebaseUid)
        return true
    }

    override suspend fun setActive(id: UUID, active: Boolean) {
        byId[id]?.let { byId[id] = it.copy(active = active) }
    }
    override suspend fun list(activeOnly: Boolean?): List<AppUser> =
        byId.values.filter { activeOnly == null || it.active == activeOnly }
    override suspend fun countAll(): Long = byId.size.toLong()
}

/** In-memory refresh-token store that honours rotation, revocation, and expiry like the real one. */
class FakeRefreshTokenRepository : RefreshTokenRepository {
    private val byHash = linkedMapOf<String, RefreshTokenRecord>()

    override suspend fun store(userId: UUID, tokenHash: String, expiresAt: Instant) {
        byHash[tokenHash] = RefreshTokenRecord(UUID.randomUUID(), userId, tokenHash, expiresAt, revoked = false)
    }

    override suspend fun findByHash(tokenHash: String): RefreshTokenRecord? = byHash[tokenHash]

    override suspend fun revoke(tokenHash: String) {
        byHash[tokenHash]?.let { byHash[tokenHash] = it.copy(revoked = true) }
    }

    override suspend fun revokeAllForUser(userId: UUID) {
        byHash.values.filter { it.userId == userId }.forEach { byHash[it.tokenHash] = it.copy(revoked = true) }
    }

    override suspend fun deleteExpired(now: Instant) {
        byHash.entries.removeIf { !it.value.expiresAt.isAfter(now) }
    }
}

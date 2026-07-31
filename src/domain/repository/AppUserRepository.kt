package com.pms.dental.domain.repository

import com.pms.dental.domain.model.AppUser
import java.util.UUID

interface AppUserRepository {
    suspend fun findByEmail(email: String): AppUser?
    suspend fun findById(id: UUID): AppUser?

    /** Resolves the local account for a verified Firebase identity. The join key is immutable. */
    suspend fun findByFirebaseUid(firebaseUid: String): AppUser?

    suspend fun insert(user: AppUser)

    /**
     * Claims an unclaimed staff invite by binding [firebaseUid] to [id], and reports whether *this*
     * call was the one that claimed it.
     *
     * Conditional on the row still having no UID, so two concurrent first sign-ins cannot both
     * succeed — the loser gets false and must re-read. Never overwrites an existing binding: a
     * Firebase identity is permanent once attached.
     */
    suspend fun bindFirebaseUid(id: UUID, firebaseUid: String): Boolean

    /** All accounts, optionally filtered by active status (null = both). For the staff admin views. */
    suspend fun list(activeOnly: Boolean?): List<AppUser>

    /**
     * Flips the active flag. The per-request auth check reads `active`, so this is the
     * instant-revocation lever for both LOCAL and FIREBASE accounts.
     */
    suspend fun setActive(id: UUID, active: Boolean)

    /** Total accounts, used by first-run bootstrap to decide whether to seed. */
    suspend fun countAll(): Long
}

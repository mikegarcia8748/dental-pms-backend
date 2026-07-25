package com.pms.dental.domain.model

import java.time.Instant
import java.util.UUID

/**
 * One entry in the audit trail: which user did what to which entity, and when. Required now
 * that more than one person can sign in — every create/edit/deactivate records the acting user.
 */
data class AuditEntry(
    val id: UUID,
    val userId: UUID,
    val action: AuditAction,
    val entity: String,
    val entityId: UUID,
    val at: Instant,
)

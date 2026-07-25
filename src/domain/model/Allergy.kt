package com.pms.dental.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A structured, safety-critical allergy (esp. to anesthetics). Kept as its own record — not
 * buried in free text — so it can be surfaced prominently before a procedure. [active] gates
 * soft-deletion; there are no hard deletes.
 */
data class Allergy(
    val id: UUID,
    val patientId: UUID,
    val substance: String,
    val severity: AllergySeverity?,
    val note: String?,
    val active: Boolean,
    val recordedBy: UUID,
    val recordedAt: Instant,
)

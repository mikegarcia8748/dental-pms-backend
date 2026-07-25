package com.pms.dental.domain.model

import java.time.Instant
import java.util.UUID

/**
 * A recorded consent acknowledgment (v1 captures the acknowledgment, not a signature).
 * [textVersion] pins which [ConsentText] version the patient was shown, so the record stays
 * accurate even after the consent wording is later revised.
 */
data class Consent(
    val id: UUID,
    val patientId: UUID,
    val type: ConsentType,
    val textVersion: String,
    val acknowledgedByRole: AcknowledgedByRole,
    val acknowledgedByName: String?,
    val acknowledgedAt: Instant,
    val recordedBy: UUID,
    val recordedAt: Instant,
)

package com.pms.dental.domain.model

import java.util.UUID

/**
 * A versioned consent-text template (seeded; the PDA informed-consent body and the RA 10173
 * data-privacy text). A [Consent] references one of these by (type, version).
 */
data class ConsentText(
    val id: UUID,
    val type: ConsentType,
    val version: String,
    val title: String,
    val body: String,
    val active: Boolean,
)

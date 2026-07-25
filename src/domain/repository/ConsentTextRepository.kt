package com.pms.dental.domain.repository

import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType

interface ConsentTextRepository {
    /** Active consent texts, optionally filtered to one type. */
    suspend fun listActive(type: ConsentType?): List<ConsentText>

    /** Resolve a specific (type, version) to validate a consent acknowledgment references it. */
    suspend fun find(type: ConsentType, version: String): ConsentText?
}

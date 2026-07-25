package com.pms.dental.domain.repository

import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import java.util.UUID

interface IntakeQuestionRepository {
    /** Active questions, optionally filtered to one section, ordered by display order. */
    suspend fun listActive(section: IntakeSection?): List<IntakeQuestion>

    /** Look up specific questions (any version/active state) to validate submitted answers. */
    suspend fun findByIds(ids: Collection<UUID>): List<IntakeQuestion>
}

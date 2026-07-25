package com.pms.dental.domain.model

import java.util.UUID

/**
 * A template question in the configurable intake questionnaire (seeded from the PDA form).
 * [code] is the stable machine key; [version] + [active] let the clinic revise the form later
 * (insert a new version, deactivate the old) while historical answers keep pointing at the
 * exact question they answered. [choices] holds a JSON array for [IntakeAnswerType.CHOICE].
 */
data class IntakeQuestion(
    val id: UUID,
    val section: IntakeSection,
    val code: String,
    val prompt: String,
    val answerType: IntakeAnswerType,
    val choices: String?,
    val displayOrder: Int,
    val version: Int,
    val active: Boolean,
)

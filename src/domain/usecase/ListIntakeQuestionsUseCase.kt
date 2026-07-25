package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.repository.IntakeQuestionRepository

/**
 * Business rule: return the active intake question set (to render the form), optionally filtered
 * to one section, in display order.
 */
class ListIntakeQuestionsUseCase(private val questions: IntakeQuestionRepository) {
    suspend operator fun invoke(section: IntakeSection?): List<IntakeQuestion> = questions.listActive(section)
}

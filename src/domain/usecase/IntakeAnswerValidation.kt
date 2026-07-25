package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.NewAnswer

/**
 * A submitted answer must populate exactly the one value slot that matches its question's
 * [IntakeAnswerType] (and leave the others null). Shared by registration and answer upsert.
 */
internal fun answerMatchesType(type: IntakeAnswerType, answer: NewAnswer): Boolean = when (type) {
    IntakeAnswerType.BOOLEAN -> answer.answerBoolean != null && answer.answerText == null && answer.answerDate == null
    IntakeAnswerType.TEXT, IntakeAnswerType.CHOICE ->
        answer.answerText != null && answer.answerBoolean == null && answer.answerDate == null
    IntakeAnswerType.DATE -> answer.answerDate != null && answer.answerBoolean == null && answer.answerText == null
}

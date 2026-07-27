package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.NewAnswer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonPrimitive

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

/**
 * For a [IntakeAnswerType.CHOICE] question, the submitted [NewAnswer.answerText] must be one of the
 * question's allowed [IntakeQuestion.choices] (a JSON array of strings). Non-CHOICE questions have
 * no such constraint. A missing/unparseable `choices` list is treated leniently (no constraint) so
 * a malformed seed can't reject otherwise-valid clinical input. Call *after* [answerMatchesType].
 */
internal fun answerInChoices(question: IntakeQuestion, answer: NewAnswer): Boolean {
    if (question.answerType != IntakeAnswerType.CHOICE) return true
    val allowed = parseChoices(question.choices) ?: return true
    return answer.answerText in allowed
}

private fun parseChoices(choices: String?): Set<String>? {
    if (choices.isNullOrBlank()) return null
    return runCatching {
        (Json.parseToJsonElement(choices) as JsonArray).map { it.jsonPrimitive.content }.toSet()
    }.getOrNull()
}

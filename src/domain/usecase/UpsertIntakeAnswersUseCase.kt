package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.NewAnswer
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.IntakeQuestionRepository
import com.pms.dental.domain.repository.PatientIntakeAnswerRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.util.UUID

sealed interface UpsertAnswersResult {
    data class Success(val answers: List<PatientIntakeAnswer>) : UpsertAnswersResult
    data class Rejected(val error: UpsertAnswersError) : UpsertAnswersResult
}

enum class UpsertAnswersError { PatientNotFound, UnknownQuestion, AnswerTypeMismatch }

/**
 * Business rule: set/replace a patient's intake answers. The patient must exist, and every answer
 * must reference an active question and be answered in that question's type. All-or-nothing: if any
 * answer is invalid, none are written.
 */
class UpsertIntakeAnswersUseCase(
    private val patients: PatientRepository,
    private val questions: IntakeQuestionRepository,
    private val answers: PatientIntakeAnswerRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
) {
    suspend operator fun invoke(
        patientId: UUID,
        inputs: List<NewAnswer>,
        actingUserId: UUID,
    ): UpsertAnswersResult {
        if (!patients.existsById(patientId)) return UpsertAnswersResult.Rejected(UpsertAnswersError.PatientNotFound)

        val known = questions.findByIds(inputs.map { it.questionId }.toSet()).associateBy { it.id }
        for (input in inputs) {
            val question = known[input.questionId]
            if (question == null || !question.active) {
                return UpsertAnswersResult.Rejected(UpsertAnswersError.UnknownQuestion)
            }
            if (!answerMatchesType(question.answerType, input)) {
                return UpsertAnswersResult.Rejected(UpsertAnswersError.AnswerTypeMismatch)
            }
        }

        val now = clock.now()
        val rows = inputs.map { input ->
            PatientIntakeAnswer(
                id = idGenerator.newId(),
                patientId = patientId,
                questionId = input.questionId,
                answerBoolean = input.answerBoolean,
                answerText = input.answerText?.trim(),
                answerDate = input.answerDate,
                recordedBy = actingUserId,
                recordedAt = now,
            )
        }
        answers.upsertAll(rows)
        rows.forEach { audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.UPDATE, "intake_answer", it.id, now)) }
        return UpsertAnswersResult.Success(rows)
    }
}

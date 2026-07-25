package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.NewAnswer
import com.pms.dental.domain.service.Clock
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakeIntakeQuestionRepository
import com.pms.dental.support.FakePatientIntakeAnswerRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.FIXED_NOW
import com.pms.dental.support.SequentialIds
import com.pms.dental.support.patient
import com.pms.dental.support.question
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.UUID

private class AnswersFixture {
    val patients = FakePatientRepository()
    val questions = FakeIntakeQuestionRepository()
    val answers = FakePatientIntakeAnswerRepository()
    val audit = FakeAuditLogRepository()
    val useCase = UpsertIntakeAnswersUseCase(patients, questions, answers, audit, Clock { FIXED_NOW }, SequentialIds())
}

class UpsertIntakeAnswersUseCaseTest : FunSpec({

    test("intake answers - unknown patient - PatientNotFound") {
        val f = AnswersFixture()

        f.useCase(UUID.randomUUID(), emptyList(), UUID.randomUUID())
            .shouldBeInstanceOf<UpsertAnswersResult.Rejected>().error shouldBe UpsertAnswersError.PatientNotFound
    }

    test("intake answers - value type matches question - upserts one row per question") {
        val f = AnswersFixture()
        val p = patient()
        f.patients.seed(p)
        val boolQ = question("uses_tobacco", IntakeAnswerType.BOOLEAN)
        val textQ = question("blood_type", IntakeAnswerType.TEXT, displayOrder = 2)
        f.questions.seed(boolQ)
        f.questions.seed(textQ)

        val result = f.useCase(
            p.id,
            listOf(
                NewAnswer(boolQ.id, answerBoolean = false, answerText = null, answerDate = null),
                NewAnswer(textQ.id, answerBoolean = null, answerText = "O+", answerDate = null),
            ),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpsertAnswersResult.Success>()

        result.answers shouldHaveSize 2
        f.answers.listByPatient(p.id) shouldHaveSize 2
    }

    test("intake answers - DATE value on a BOOLEAN question - rejected with AnswerTypeMismatch") {
        val f = AnswersFixture()
        val p = patient()
        f.patients.seed(p)
        val boolQ = question("uses_tobacco", IntakeAnswerType.BOOLEAN)
        f.questions.seed(boolQ)

        f.useCase(
            p.id,
            listOf(NewAnswer(boolQ.id, answerBoolean = null, answerText = null, answerDate = LocalDate.of(2020, 1, 1))),
            UUID.randomUUID(),
        ).shouldBeInstanceOf<UpsertAnswersResult.Rejected>().error shouldBe UpsertAnswersError.AnswerTypeMismatch
    }

    test("intake answers - re-answering a question - overwrites the prior answer") {
        val f = AnswersFixture()
        val p = patient()
        f.patients.seed(p)
        val boolQ = question("good_health", IntakeAnswerType.BOOLEAN)
        f.questions.seed(boolQ)
        val actor = UUID.randomUUID()

        f.useCase(p.id, listOf(NewAnswer(boolQ.id, answerBoolean = true, answerText = null, answerDate = null)), actor)
        f.useCase(p.id, listOf(NewAnswer(boolQ.id, answerBoolean = false, answerText = null, answerDate = null)), actor)

        val stored = f.answers.listByPatient(p.id)
        stored shouldHaveSize 1
        stored.single().answerBoolean shouldBe false
    }
})

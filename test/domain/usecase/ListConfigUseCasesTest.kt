package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.support.FakeConsentTextRepository
import com.pms.dental.support.FakeIntakeQuestionRepository
import com.pms.dental.support.consentText
import com.pms.dental.support.question
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class ListConfigUseCasesTest : FunSpec({

    test("intake questions - section MEDICAL - returns only active medical questions in display order") {
        val questions = FakeIntakeQuestionRepository()
        questions.seed(question("q2", IntakeAnswerType.TEXT, IntakeSection.MEDICAL, displayOrder = 2))
        questions.seed(question("q1", IntakeAnswerType.BOOLEAN, IntakeSection.MEDICAL, displayOrder = 1))
        questions.seed(question("d1", IntakeAnswerType.TEXT, IntakeSection.DENTAL, displayOrder = 1))
        questions.seed(question("q3_inactive", IntakeAnswerType.TEXT, IntakeSection.MEDICAL, active = false, displayOrder = 3))
        val useCase = ListIntakeQuestionsUseCase(questions)

        useCase(IntakeSection.MEDICAL).map { it.code } shouldContainExactly listOf("q1", "q2")
    }

    test("intake questions - no section filter - returns both sections") {
        val questions = FakeIntakeQuestionRepository()
        questions.seed(question("m1", IntakeAnswerType.BOOLEAN, IntakeSection.MEDICAL, displayOrder = 1))
        questions.seed(question("d1", IntakeAnswerType.TEXT, IntakeSection.DENTAL, displayOrder = 2))
        val useCase = ListIntakeQuestionsUseCase(questions)

        useCase(null).map { it.section }.toSet() shouldBe setOf(IntakeSection.MEDICAL, IntakeSection.DENTAL)
    }

    test("consent texts - type TREATMENT - returns the active treatment text") {
        val texts = FakeConsentTextRepository()
        texts.seed(consentText(ConsentType.TREATMENT, "PDA-2010"))
        texts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        val useCase = ListConsentTextsUseCase(texts)

        useCase(ConsentType.TREATMENT).single().type shouldBe ConsentType.TREATMENT
    }

    test("consent texts - no type filter - returns all active texts and excludes inactive") {
        val texts = FakeConsentTextRepository()
        texts.seed(consentText(ConsentType.TREATMENT, "PDA-2010"))
        texts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        texts.seed(consentText(ConsentType.RADIOGRAPH, "old", active = false))
        val useCase = ListConsentTextsUseCase(texts)

        useCase(null) shouldHaveSize 2
    }
})

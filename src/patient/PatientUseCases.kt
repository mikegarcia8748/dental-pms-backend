package com.pms.dental.patient

import com.pms.dental.domain.usecase.AddAllergyUseCase
import com.pms.dental.domain.usecase.DeactivateAllergyUseCase
import com.pms.dental.domain.usecase.DeactivatePatientUseCase
import com.pms.dental.domain.usecase.GetPatientDetailsUseCase
import com.pms.dental.domain.usecase.ListConsentTextsUseCase
import com.pms.dental.domain.usecase.ListIntakeQuestionsUseCase
import com.pms.dental.domain.usecase.ListPatientsUseCase
import com.pms.dental.domain.usecase.ReactivatePatientUseCase
import com.pms.dental.domain.usecase.RecordConsentUseCase
import com.pms.dental.domain.usecase.RegisterPatientUseCase
import com.pms.dental.domain.usecase.UpdateAllergyUseCase
import com.pms.dental.domain.usecase.UpdatePatientUseCase
import com.pms.dental.domain.usecase.UpsertIntakeAnswersUseCase

/** Bundles the patient/intake use cases so the route function takes one dependency, not twelve. */
class PatientUseCases(
    val register: RegisterPatientUseCase,
    val list: ListPatientsUseCase,
    val details: GetPatientDetailsUseCase,
    val update: UpdatePatientUseCase,
    val deactivate: DeactivatePatientUseCase,
    val reactivate: ReactivatePatientUseCase,
    val addAllergy: AddAllergyUseCase,
    val updateAllergy: UpdateAllergyUseCase,
    val deactivateAllergy: DeactivateAllergyUseCase,
    val upsertAnswers: UpsertIntakeAnswersUseCase,
    val recordConsent: RecordConsentUseCase,
    val listQuestions: ListIntakeQuestionsUseCase,
    val listConsentTexts: ListConsentTextsUseCase,
)

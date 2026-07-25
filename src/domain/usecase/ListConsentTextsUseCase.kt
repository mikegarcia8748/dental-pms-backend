package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.repository.ConsentTextRepository

/**
 * Business rule: return the active consent texts to present to a patient, optionally filtered to
 * one type.
 */
class ListConsentTextsUseCase(private val consentTexts: ConsentTextRepository) {
    suspend operator fun invoke(type: ConsentType?): List<ConsentText> = consentTexts.listActive(type)
}

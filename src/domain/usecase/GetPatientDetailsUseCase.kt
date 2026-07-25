package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.PatientDetails
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.ConsentRepository
import com.pms.dental.domain.repository.PatientIntakeAnswerRepository
import com.pms.dental.domain.repository.PatientRepository
import java.util.UUID

sealed interface PatientDetailsResult {
    data class Found(val details: PatientDetails) : PatientDetailsResult
    data object NotFound : PatientDetailsResult
}

/**
 * Business rule: assemble a patient's full record — demographics plus their allergies, intake
 * answers, and consents. Allergies include deactivated ones so nothing is silently hidden.
 */
class GetPatientDetailsUseCase(
    private val patients: PatientRepository,
    private val allergies: AllergyRepository,
    private val answers: PatientIntakeAnswerRepository,
    private val consents: ConsentRepository,
) {
    suspend operator fun invoke(patientId: UUID): PatientDetailsResult {
        val patient = patients.findById(patientId) ?: return PatientDetailsResult.NotFound
        return PatientDetailsResult.Found(
            PatientDetails(
                patient = patient,
                allergies = allergies.listByPatient(patientId, includeInactive = true),
                answers = answers.listByPatient(patientId),
                consents = consents.listByPatient(patientId),
            ),
        )
    }
}

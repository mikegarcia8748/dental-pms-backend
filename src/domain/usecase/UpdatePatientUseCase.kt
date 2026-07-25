package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientDemographics
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.PatientRepository
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.service.IdGenerator
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.util.UUID

sealed interface UpdatePatientResult {
    data class Success(val patient: Patient) : UpdatePatientResult
    data class Rejected(val error: UpdatePatientError) : UpdatePatientResult
}

enum class UpdatePatientError { NotFound, MinorRequiresGuardian }

/**
 * Business rule: edit an existing patient's demographics / PH status / legacy fields, recording
 * who changed it. The minor-needs-guardian rule still holds. Registration date, creation
 * attribution, and active state are preserved (deactivation is a separate action).
 */
class UpdatePatientUseCase(
    private val patients: PatientRepository,
    private val audit: AuditLogRepository,
    private val clock: Clock,
    private val idGenerator: IdGenerator,
    private val zone: ZoneId = ZoneId.of("Asia/Manila"),
) {
    suspend operator fun invoke(
        patientId: UUID,
        demographics: PatientDemographics,
        actingUserId: UUID,
    ): UpdatePatientResult {
        val existing = patients.findById(patientId) ?: return UpdatePatientResult.Rejected(UpdatePatientError.NotFound)
        val now = clock.now()

        demographics.dateOfBirth?.let { dob ->
            val age = Period.between(dob, LocalDate.ofInstant(now, zone)).years
            if (age < 18 && (demographics.guardianName.isNullOrBlank() || demographics.guardianContact.isNullOrBlank())) {
                return UpdatePatientResult.Rejected(UpdatePatientError.MinorRequiresGuardian)
            }
        }

        val updated = existing.copy(
            lastName = demographics.lastName.trim(),
            firstName = demographics.firstName.trim(),
            middleName = demographics.middleName?.trim(),
            suffix = demographics.suffix?.trim(),
            nickname = demographics.nickname?.trim(),
            dateOfBirth = demographics.dateOfBirth,
            sex = demographics.sex,
            religion = demographics.religion?.trim(),
            nationality = demographics.nationality?.trim(),
            civilStatus = demographics.civilStatus?.trim(),
            occupation = demographics.occupation?.trim(),
            address = demographics.address?.trim(),
            mobileNumber = demographics.mobileNumber?.trim(),
            homeNumber = demographics.homeNumber?.trim(),
            officeNumber = demographics.officeNumber?.trim(),
            email = demographics.email?.trim()?.lowercase(),
            guardianName = demographics.guardianName?.trim(),
            guardianRelationship = demographics.guardianRelationship?.trim(),
            guardianOccupation = demographics.guardianOccupation?.trim(),
            guardianContact = demographics.guardianContact?.trim(),
            emergencyContactName = demographics.emergencyContactName?.trim(),
            emergencyContactRelationship = demographics.emergencyContactRelationship?.trim(),
            emergencyContactNumber = demographics.emergencyContactNumber?.trim(),
            isSenior = demographics.isSenior,
            isPwd = demographics.isPwd,
            scPwdIdNumber = demographics.scPwdIdNumber?.trim(),
            tin = demographics.tin?.trim(),
            dentalInsurance = demographics.dentalInsurance?.trim(),
            insuranceEffectiveDate = demographics.insuranceEffectiveDate,
            referralSource = demographics.referralSource?.trim(),
            isLegacy = demographics.isLegacy,
            legacySummary = demographics.legacySummary?.trim(),
            updatedBy = actingUserId,
            updatedAt = now,
        )

        patients.update(updated)
        audit.record(AuditEntry(idGenerator.newId(), actingUserId, AuditAction.UPDATE, "patient", patientId, now))
        return UpdatePatientResult.Success(updated)
    }
}

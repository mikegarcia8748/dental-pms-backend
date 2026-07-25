package com.pms.dental.support

import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientDemographics
import com.pms.dental.domain.model.Sex
import com.pms.dental.domain.service.IdGenerator
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/** Deterministic id source for tests: 00000000-0000-0000-0000-00000000000N. */
class SequentialIds : IdGenerator {
    private var n = 0L
    override fun newId(): UUID = UUID(0L, ++n)
}

/** A fixed "now" used across the intake tests. */
val FIXED_NOW: Instant = Instant.parse("2026-07-24T02:00:00Z")

/**
 * Build [PatientDemographics] with sensible defaults; override only what a test cares about.
 * Defaults describe a valid adult, non-legacy patient.
 */
fun demographics(
    lastName: String = "Dela Cruz",
    firstName: String = "Juan",
    dateOfBirth: LocalDate? = LocalDate.of(1990, 1, 1),
    sex: Sex = Sex.MALE,
    mobileNumber: String? = "09170000000",
    isSenior: Boolean = false,
    isPwd: Boolean = false,
    isLegacy: Boolean = false,
    legacySummary: String? = null,
    registeredAt: Instant? = null,
    guardianName: String? = null,
    guardianContact: String? = null,
): PatientDemographics = PatientDemographics(
    lastName = lastName,
    firstName = firstName,
    middleName = null,
    suffix = null,
    nickname = null,
    dateOfBirth = dateOfBirth,
    sex = sex,
    religion = null,
    nationality = null,
    civilStatus = null,
    occupation = null,
    address = null,
    mobileNumber = mobileNumber,
    homeNumber = null,
    officeNumber = null,
    email = null,
    guardianName = guardianName,
    guardianRelationship = null,
    guardianOccupation = null,
    guardianContact = guardianContact,
    emergencyContactName = null,
    emergencyContactRelationship = null,
    emergencyContactNumber = null,
    isSenior = isSenior,
    isPwd = isPwd,
    scPwdIdNumber = null,
    tin = null,
    dentalInsurance = null,
    insuranceEffectiveDate = null,
    referralSource = null,
    isLegacy = isLegacy,
    legacySummary = legacySummary,
    registeredAt = registeredAt,
)

fun question(
    code: String,
    answerType: IntakeAnswerType,
    section: IntakeSection = IntakeSection.MEDICAL,
    active: Boolean = true,
    displayOrder: Int = 1,
    id: UUID = UUID.randomUUID(),
): IntakeQuestion = IntakeQuestion(
    id = id,
    section = section,
    code = code,
    prompt = code,
    answerType = answerType,
    choices = null,
    displayOrder = displayOrder,
    version = 1,
    active = active,
)

fun consentText(
    type: ConsentType,
    version: String,
    active: Boolean = true,
    id: UUID = UUID.randomUUID(),
): ConsentText = ConsentText(
    id = id,
    type = type,
    version = version,
    title = "$type $version",
    body = "body",
    active = active,
)

/** A minimal persisted patient for seeding read/update tests. */
fun patient(
    id: UUID = UUID.randomUUID(),
    active: Boolean = true,
    createdBy: UUID = UUID.randomUUID(),
    registeredAt: Instant = FIXED_NOW,
    lastName: String = "Dela Cruz",
    firstName: String = "Juan",
): Patient = Patient(
    id = id,
    lastName = lastName,
    firstName = firstName,
    middleName = null,
    suffix = null,
    nickname = null,
    dateOfBirth = LocalDate.of(1990, 1, 1),
    sex = Sex.MALE,
    religion = null,
    nationality = null,
    civilStatus = null,
    occupation = null,
    address = null,
    mobileNumber = "09170000000",
    homeNumber = null,
    officeNumber = null,
    email = null,
    guardianName = null,
    guardianRelationship = null,
    guardianOccupation = null,
    guardianContact = null,
    emergencyContactName = null,
    emergencyContactRelationship = null,
    emergencyContactNumber = null,
    isSenior = false,
    isPwd = false,
    scPwdIdNumber = null,
    tin = null,
    dentalInsurance = null,
    insuranceEffectiveDate = null,
    referralSource = null,
    isLegacy = false,
    legacySummary = null,
    registeredAt = registeredAt,
    active = active,
    createdBy = createdBy,
    createdAt = registeredAt,
    updatedBy = null,
    updatedAt = null,
)

@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AllergySeverity
import com.pms.dental.domain.model.AuditAction
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.model.Sex
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.jdbc.insert
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * Row writers (insert into the mirror tables) and row mappers (ResultRow → domain). The writers
 * take no transaction of their own — callers run them inside a `dbQuery { }` so a whole
 * registration commits atomically.
 */

internal fun insertPatientRow(p: Patient) {
    Patients.insert {
        it[id] = p.id.toKotlinUuid()
        it[lastName] = p.lastName
        it[firstName] = p.firstName
        it[middleName] = p.middleName
        it[suffix] = p.suffix
        it[nickname] = p.nickname
        it[dateOfBirth] = p.dateOfBirth
        it[sex] = p.sex.name
        it[religion] = p.religion
        it[nationality] = p.nationality
        it[civilStatus] = p.civilStatus
        it[occupation] = p.occupation
        it[address] = p.address
        it[mobileNumber] = p.mobileNumber
        it[homeNumber] = p.homeNumber
        it[officeNumber] = p.officeNumber
        it[email] = p.email
        it[guardianName] = p.guardianName
        it[guardianRelationship] = p.guardianRelationship
        it[guardianOccupation] = p.guardianOccupation
        it[guardianContact] = p.guardianContact
        it[emergencyContactName] = p.emergencyContactName
        it[emergencyContactRelationship] = p.emergencyContactRelationship
        it[emergencyContactNumber] = p.emergencyContactNumber
        it[isSenior] = p.isSenior
        it[isPwd] = p.isPwd
        it[scPwdIdNumber] = p.scPwdIdNumber
        it[tin] = p.tin
        it[dentalInsurance] = p.dentalInsurance
        it[insuranceEffectiveDate] = p.insuranceEffectiveDate
        it[referralSource] = p.referralSource
        it[isLegacy] = p.isLegacy
        it[legacySummary] = p.legacySummary
        it[registeredAt] = p.registeredAt
        it[active] = p.active
        it[createdBy] = p.createdBy.toKotlinUuid()
        it[createdAt] = p.createdAt
        it[updatedBy] = p.updatedBy?.toKotlinUuid()
        it[updatedAt] = p.updatedAt
    }
}

internal fun insertAllergyRow(a: Allergy) {
    Allergies.insert {
        it[id] = a.id.toKotlinUuid()
        it[patientId] = a.patientId.toKotlinUuid()
        it[substance] = a.substance
        it[severity] = a.severity?.name
        it[note] = a.note
        it[active] = a.active
        it[recordedBy] = a.recordedBy.toKotlinUuid()
        it[recordedAt] = a.recordedAt
    }
}

internal fun insertAnswerRow(a: PatientIntakeAnswer) {
    PatientIntakeAnswers.insert {
        it[id] = a.id.toKotlinUuid()
        it[patientId] = a.patientId.toKotlinUuid()
        it[questionId] = a.questionId.toKotlinUuid()
        it[answerBoolean] = a.answerBoolean
        it[answerText] = a.answerText
        it[answerDate] = a.answerDate
        it[recordedBy] = a.recordedBy.toKotlinUuid()
        it[recordedAt] = a.recordedAt
    }
}

internal fun insertConsentRow(c: Consent) {
    Consents.insert {
        it[id] = c.id.toKotlinUuid()
        it[patientId] = c.patientId.toKotlinUuid()
        it[type] = c.type.name
        it[textVersion] = c.textVersion
        it[acknowledgedByRole] = c.acknowledgedByRole.name
        it[acknowledgedByName] = c.acknowledgedByName
        it[acknowledgedAt] = c.acknowledgedAt
        it[recordedBy] = c.recordedBy.toKotlinUuid()
        it[recordedAt] = c.recordedAt
    }
}

internal fun insertAuditRow(e: AuditEntry) {
    AuditLogs.insert {
        it[id] = e.id.toKotlinUuid()
        it[userId] = e.userId.toKotlinUuid()
        it[action] = e.action.name
        it[entity] = e.entity
        it[entityId] = e.entityId.toKotlinUuid()
        it[at] = e.at
    }
}

internal fun ResultRow.toPatient() = Patient(
    id = this[Patients.id].toJavaUuid(),
    lastName = this[Patients.lastName],
    firstName = this[Patients.firstName],
    middleName = this[Patients.middleName],
    suffix = this[Patients.suffix],
    nickname = this[Patients.nickname],
    dateOfBirth = this[Patients.dateOfBirth],
    sex = Sex.valueOf(this[Patients.sex]),
    religion = this[Patients.religion],
    nationality = this[Patients.nationality],
    civilStatus = this[Patients.civilStatus],
    occupation = this[Patients.occupation],
    address = this[Patients.address],
    mobileNumber = this[Patients.mobileNumber],
    homeNumber = this[Patients.homeNumber],
    officeNumber = this[Patients.officeNumber],
    email = this[Patients.email],
    guardianName = this[Patients.guardianName],
    guardianRelationship = this[Patients.guardianRelationship],
    guardianOccupation = this[Patients.guardianOccupation],
    guardianContact = this[Patients.guardianContact],
    emergencyContactName = this[Patients.emergencyContactName],
    emergencyContactRelationship = this[Patients.emergencyContactRelationship],
    emergencyContactNumber = this[Patients.emergencyContactNumber],
    isSenior = this[Patients.isSenior],
    isPwd = this[Patients.isPwd],
    scPwdIdNumber = this[Patients.scPwdIdNumber],
    tin = this[Patients.tin],
    dentalInsurance = this[Patients.dentalInsurance],
    insuranceEffectiveDate = this[Patients.insuranceEffectiveDate],
    referralSource = this[Patients.referralSource],
    isLegacy = this[Patients.isLegacy],
    legacySummary = this[Patients.legacySummary],
    registeredAt = this[Patients.registeredAt],
    active = this[Patients.active],
    createdBy = this[Patients.createdBy].toJavaUuid(),
    createdAt = this[Patients.createdAt],
    updatedBy = this[Patients.updatedBy]?.toJavaUuid(),
    updatedAt = this[Patients.updatedAt],
)

internal fun ResultRow.toAllergy() = Allergy(
    id = this[Allergies.id].toJavaUuid(),
    patientId = this[Allergies.patientId].toJavaUuid(),
    substance = this[Allergies.substance],
    severity = this[Allergies.severity]?.let { AllergySeverity.valueOf(it) },
    note = this[Allergies.note],
    active = this[Allergies.active],
    recordedBy = this[Allergies.recordedBy].toJavaUuid(),
    recordedAt = this[Allergies.recordedAt],
)

internal fun ResultRow.toIntakeQuestion() = IntakeQuestion(
    id = this[IntakeQuestions.id].toJavaUuid(),
    section = IntakeSection.valueOf(this[IntakeQuestions.section]),
    code = this[IntakeQuestions.code],
    prompt = this[IntakeQuestions.prompt],
    answerType = IntakeAnswerType.valueOf(this[IntakeQuestions.answerType]),
    choices = this[IntakeQuestions.choices],
    displayOrder = this[IntakeQuestions.displayOrder],
    version = this[IntakeQuestions.version],
    active = this[IntakeQuestions.active],
)

internal fun ResultRow.toAnswer() = PatientIntakeAnswer(
    id = this[PatientIntakeAnswers.id].toJavaUuid(),
    patientId = this[PatientIntakeAnswers.patientId].toJavaUuid(),
    questionId = this[PatientIntakeAnswers.questionId].toJavaUuid(),
    answerBoolean = this[PatientIntakeAnswers.answerBoolean],
    answerText = this[PatientIntakeAnswers.answerText],
    answerDate = this[PatientIntakeAnswers.answerDate],
    recordedBy = this[PatientIntakeAnswers.recordedBy].toJavaUuid(),
    recordedAt = this[PatientIntakeAnswers.recordedAt],
)

internal fun ResultRow.toConsent() = Consent(
    id = this[Consents.id].toJavaUuid(),
    patientId = this[Consents.patientId].toJavaUuid(),
    type = ConsentType.valueOf(this[Consents.type]),
    textVersion = this[Consents.textVersion],
    acknowledgedByRole = AcknowledgedByRole.valueOf(this[Consents.acknowledgedByRole]),
    acknowledgedByName = this[Consents.acknowledgedByName],
    acknowledgedAt = this[Consents.acknowledgedAt],
    recordedBy = this[Consents.recordedBy].toJavaUuid(),
    recordedAt = this[Consents.recordedAt],
)

internal fun ResultRow.toConsentText() = ConsentText(
    id = this[ConsentTexts.id].toJavaUuid(),
    type = ConsentType.valueOf(this[ConsentTexts.type]),
    version = this[ConsentTexts.version],
    title = this[ConsentTexts.title],
    body = this[ConsentTexts.body],
    active = this[ConsentTexts.active],
)

internal fun ResultRow.toAuditEntry() = AuditEntry(
    id = this[AuditLogs.id].toJavaUuid(),
    userId = this[AuditLogs.userId].toJavaUuid(),
    action = AuditAction.valueOf(this[AuditLogs.action]),
    entity = this[AuditLogs.entity],
    entityId = this[AuditLogs.entityId].toJavaUuid(),
    at = this[AuditLogs.at],
)

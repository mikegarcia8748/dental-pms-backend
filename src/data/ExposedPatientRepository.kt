@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.repository.PatientRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.like
import org.jetbrains.exposed.v1.core.lowerCase
import org.jetbrains.exposed.v1.core.or
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class ExposedPatientRepository : PatientRepository {

    override suspend fun insertRegistration(
        patient: Patient,
        allergies: List<Allergy>,
        answers: List<PatientIntakeAnswer>,
        consents: List<Consent>,
        audit: List<AuditEntry>,
    ): Unit = dbQuery {
        insertPatientRow(patient)
        allergies.forEach { insertAllergyRow(it) }
        answers.forEach { insertAnswerRow(it) }
        consents.forEach { insertConsentRow(it) }
        audit.forEach { insertAuditRow(it) }
    }

    override suspend fun findById(id: UUID): Patient? = dbQuery {
        Patients.selectAll().where { Patients.id eq id.toKotlinUuid() }.singleOrNull()?.toPatient()
    }

    override suspend fun existsById(id: UUID): Boolean = dbQuery {
        Patients.selectAll().where { Patients.id eq id.toKotlinUuid() }.limit(1).count() > 0
    }

    override suspend fun search(query: String, limit: Int, offset: Int, includeInactive: Boolean): List<Patient> = dbQuery {
        Patients.selectAll()
            .where { patientSearch(query, includeInactive) }
            .orderBy(Patients.registeredAt to SortOrder.DESC)
            .limit(limit)
            .offset(offset.toLong())
            .map { it.toPatient() }
    }

    override suspend fun countSearch(query: String, includeInactive: Boolean): Long = dbQuery {
        Patients.selectAll().where { patientSearch(query, includeInactive) }.count()
    }

    override suspend fun update(patient: Patient): Unit = dbQuery {
        Patients.update({ Patients.id eq patient.id.toKotlinUuid() }) {
            it[lastName] = patient.lastName
            it[firstName] = patient.firstName
            it[middleName] = patient.middleName
            it[suffix] = patient.suffix
            it[nickname] = patient.nickname
            it[dateOfBirth] = patient.dateOfBirth
            it[sex] = patient.sex.name
            it[religion] = patient.religion
            it[nationality] = patient.nationality
            it[civilStatus] = patient.civilStatus
            it[occupation] = patient.occupation
            it[address] = patient.address
            it[mobileNumber] = patient.mobileNumber
            it[homeNumber] = patient.homeNumber
            it[officeNumber] = patient.officeNumber
            it[email] = patient.email
            it[guardianName] = patient.guardianName
            it[guardianRelationship] = patient.guardianRelationship
            it[guardianOccupation] = patient.guardianOccupation
            it[guardianContact] = patient.guardianContact
            it[emergencyContactName] = patient.emergencyContactName
            it[emergencyContactRelationship] = patient.emergencyContactRelationship
            it[emergencyContactNumber] = patient.emergencyContactNumber
            it[isSenior] = patient.isSenior
            it[isPwd] = patient.isPwd
            it[scPwdIdNumber] = patient.scPwdIdNumber
            it[tin] = patient.tin
            it[dentalInsurance] = patient.dentalInsurance
            it[insuranceEffectiveDate] = patient.insuranceEffectiveDate
            it[referralSource] = patient.referralSource
            it[isLegacy] = patient.isLegacy
            it[legacySummary] = patient.legacySummary
            it[active] = patient.active
            it[updatedBy] = patient.updatedBy?.toKotlinUuid()
            it[updatedAt] = patient.updatedAt
        }
    }
}

/** Name search (case-insensitive on last/first) plus the active filter, as a reusable predicate. */
private fun patientSearch(query: String, includeInactive: Boolean): Op<Boolean> {
    val activeOp: Op<Boolean> = if (includeInactive) Op.TRUE else (Patients.active eq true)
    if (query.isBlank()) return activeOp
    val pattern = "%${query.trim().lowercase()}%"
    val byName = (Patients.lastName.lowerCase() like pattern) or (Patients.firstName.lowerCase() like pattern)
    return activeOp and byName
}

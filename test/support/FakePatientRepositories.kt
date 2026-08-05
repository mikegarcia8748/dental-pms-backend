package com.pms.dental.support

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.repository.AllergyRepository
import com.pms.dental.domain.repository.AuditLogRepository
import com.pms.dental.domain.repository.ConsentRepository
import com.pms.dental.domain.repository.ConsentTextRepository
import com.pms.dental.domain.repository.IntakeQuestionRepository
import com.pms.dental.domain.repository.PatientIntakeAnswerRepository
import com.pms.dental.domain.repository.PatientRepository
import java.util.UUID

/** In-memory patient store. Captures each registration so tests can assert what was persisted. */
class FakePatientRepository : PatientRepository {
    val patients = linkedMapOf<UUID, Patient>()
    val registrations = mutableListOf<Registration>()

    /** When true, [insertRegistration] throws before storing anything — models a rolled-back tx. */
    var failOnInsert = false

    data class Registration(
        val patient: Patient,
        val allergies: List<Allergy>,
        val answers: List<PatientIntakeAnswer>,
        val consents: List<Consent>,
        val audit: List<AuditEntry>,
    )

    override suspend fun insertRegistration(
        patient: Patient,
        allergies: List<Allergy>,
        answers: List<PatientIntakeAnswer>,
        consents: List<Consent>,
        audit: List<AuditEntry>,
    ) {
        if (failOnInsert) throw IllegalStateException("simulated insert failure")
        patients[patient.id] = patient
        registrations += Registration(patient, allergies, answers, consents, audit)
    }

    override suspend fun findById(id: UUID): Patient? = patients[id]
    override suspend fun existsById(id: UUID): Boolean = patients.containsKey(id)

    override suspend fun search(query: String, limit: Int, offset: Int, includeInactive: Boolean): List<Patient> =
        patients.values
            .filter { (includeInactive || it.active) && it.matches(query) }
            .sortedByDescending { it.registeredAt }
            .drop(offset)
            .take(limit)

    override suspend fun countSearch(query: String, includeInactive: Boolean): Long =
        patients.values.count { (includeInactive || it.active) && it.matches(query) }.toLong()

    override suspend fun update(patient: Patient) { patients[patient.id] = patient }

    fun seed(patient: Patient) { patients[patient.id] = patient }

    /** Mirrors `patientSearch` in ExposedPatientRepository: last name, first name, or mobile number. */
    private fun Patient.matches(query: String): Boolean =
        query.isBlank() ||
            lastName.contains(query, ignoreCase = true) ||
            firstName.contains(query, ignoreCase = true) ||
            mobileNumber?.contains(query, ignoreCase = true) == true
}

class FakeAllergyRepository : AllergyRepository {
    val byId = linkedMapOf<UUID, Allergy>()

    override suspend fun insert(allergy: Allergy) { byId[allergy.id] = allergy }
    override suspend fun findById(id: UUID): Allergy? = byId[id]
    override suspend fun listByPatient(patientId: UUID, includeInactive: Boolean): List<Allergy> =
        byId.values.filter { it.patientId == patientId && (includeInactive || it.active) }
    override suspend fun update(allergy: Allergy) { byId[allergy.id] = allergy }

    fun seed(allergy: Allergy) { byId[allergy.id] = allergy }
}

class FakeIntakeQuestionRepository : IntakeQuestionRepository {
    val byId = linkedMapOf<UUID, IntakeQuestion>()

    override suspend fun listActive(section: IntakeSection?): List<IntakeQuestion> =
        byId.values.filter { it.active && (section == null || it.section == section) }.sortedBy { it.displayOrder }
    override suspend fun findByIds(ids: Collection<UUID>): List<IntakeQuestion> =
        byId.values.filter { it.id in ids }

    fun seed(question: IntakeQuestion) { byId[question.id] = question }
}

class FakePatientIntakeAnswerRepository : PatientIntakeAnswerRepository {
    val byPatientQuestion = linkedMapOf<Pair<UUID, UUID>, PatientIntakeAnswer>()

    override suspend fun upsertAll(answers: List<PatientIntakeAnswer>) {
        answers.forEach { byPatientQuestion[it.patientId to it.questionId] = it }
    }
    override suspend fun listByPatient(patientId: UUID): List<PatientIntakeAnswer> =
        byPatientQuestion.values.filter { it.patientId == patientId }
}

class FakeConsentRepository : ConsentRepository {
    val items = mutableListOf<Consent>()

    override suspend fun insert(consent: Consent) { items += consent }
    override suspend fun listByPatient(patientId: UUID): List<Consent> = items.filter { it.patientId == patientId }
}

class FakeConsentTextRepository : ConsentTextRepository {
    val items = mutableListOf<ConsentText>()

    override suspend fun listActive(type: ConsentType?): List<ConsentText> =
        items.filter { it.active && (type == null || it.type == type) }
    override suspend fun find(type: ConsentType, version: String): ConsentText? =
        items.firstOrNull { it.type == type && it.version == version }

    fun seed(text: ConsentText) { items += text }
}

class FakeAuditLogRepository : AuditLogRepository {
    val entries = mutableListOf<AuditEntry>()

    override suspend fun record(entry: AuditEntry) { entries += entry }
}

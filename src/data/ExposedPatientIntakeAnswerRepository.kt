@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.repository.PatientIntakeAnswerRepository
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class ExposedPatientIntakeAnswerRepository : PatientIntakeAnswerRepository {

    /** Overwrite each (patient, question): delete any prior answer, then insert the new one. */
    override suspend fun upsertAll(answers: List<PatientIntakeAnswer>): Unit = dbQuery {
        answers.forEach { answer ->
            PatientIntakeAnswers.deleteWhere {
                (patientId eq answer.patientId.toKotlinUuid()) and (questionId eq answer.questionId.toKotlinUuid())
            }
            insertAnswerRow(answer)
        }
    }

    override suspend fun listByPatient(patientId: UUID): List<PatientIntakeAnswer> = dbQuery {
        PatientIntakeAnswers.selectAll()
            .where { PatientIntakeAnswers.patientId eq patientId.toKotlinUuid() }
            .map { it.toAnswer() }
    }
}

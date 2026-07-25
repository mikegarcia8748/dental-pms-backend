@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.repository.AllergyRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class ExposedAllergyRepository : AllergyRepository {

    override suspend fun insert(allergy: Allergy): Unit = dbQuery { insertAllergyRow(allergy) }

    override suspend fun findById(id: UUID): Allergy? = dbQuery {
        Allergies.selectAll().where { Allergies.id eq id.toKotlinUuid() }.singleOrNull()?.toAllergy()
    }

    override suspend fun listByPatient(patientId: UUID, includeInactive: Boolean): List<Allergy> = dbQuery {
        val patientOp = Allergies.patientId eq patientId.toKotlinUuid()
        val predicate: Op<Boolean> = if (includeInactive) patientOp else patientOp and (Allergies.active eq true)
        Allergies.selectAll().where { predicate }.map { it.toAllergy() }
    }

    override suspend fun update(allergy: Allergy): Unit = dbQuery {
        Allergies.update({ Allergies.id eq allergy.id.toKotlinUuid() }) {
            it[substance] = allergy.substance
            it[severity] = allergy.severity?.name
            it[note] = allergy.note
            it[active] = allergy.active
        }
    }
}

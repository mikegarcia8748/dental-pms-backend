@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.repository.IntakeQuestionRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class ExposedIntakeQuestionRepository : IntakeQuestionRepository {

    override suspend fun listActive(section: IntakeSection?): List<IntakeQuestion> = dbQuery {
        val activeOp = IntakeQuestions.active eq true
        val predicate: Op<Boolean> = if (section == null) activeOp else activeOp and (IntakeQuestions.section eq section.name)
        IntakeQuestions.selectAll()
            .where { predicate }
            .orderBy(IntakeQuestions.displayOrder to SortOrder.ASC)
            .map { it.toIntakeQuestion() }
    }

    override suspend fun findByIds(ids: Collection<UUID>): List<IntakeQuestion> = dbQuery {
        if (ids.isEmpty()) {
            emptyList()
        } else {
            val kids = ids.map { it.toKotlinUuid() }
            IntakeQuestions.selectAll().where { IntakeQuestions.id inList kids }.map { it.toIntakeQuestion() }
        }
    }
}

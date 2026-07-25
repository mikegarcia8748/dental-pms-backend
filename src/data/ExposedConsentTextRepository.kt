package com.pms.dental.data

import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.repository.ConsentTextRepository
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll

class ExposedConsentTextRepository : ConsentTextRepository {

    override suspend fun listActive(type: ConsentType?): List<ConsentText> = dbQuery {
        val activeOp = ConsentTexts.active eq true
        val predicate: Op<Boolean> = if (type == null) activeOp else activeOp and (ConsentTexts.type eq type.name)
        ConsentTexts.selectAll().where { predicate }.map { it.toConsentText() }
    }

    override suspend fun find(type: ConsentType, version: String): ConsentText? = dbQuery {
        ConsentTexts.selectAll()
            .where { (ConsentTexts.type eq type.name) and (ConsentTexts.version eq version) }
            .singleOrNull()
            ?.toConsentText()
    }
}

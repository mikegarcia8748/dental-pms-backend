@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.repository.ConsentRepository
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

class ExposedConsentRepository : ConsentRepository {

    override suspend fun insert(consent: Consent): Unit = dbQuery { insertConsentRow(consent) }

    override suspend fun listByPatient(patientId: UUID): List<Consent> = dbQuery {
        Consents.selectAll().where { Consents.patientId eq patientId.toKotlinUuid() }.map { it.toConsent() }
    }
}

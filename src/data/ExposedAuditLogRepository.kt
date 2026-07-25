package com.pms.dental.data

import com.pms.dental.domain.model.AuditEntry
import com.pms.dental.domain.repository.AuditLogRepository

class ExposedAuditLogRepository : AuditLogRepository {
    override suspend fun record(entry: AuditEntry): Unit = dbQuery { insertAuditRow(entry) }
}

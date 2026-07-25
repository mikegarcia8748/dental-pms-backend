package com.pms.dental.domain.repository

import com.pms.dental.domain.model.AuditEntry

interface AuditLogRepository {
    /** Append one audit entry. (Registration writes its entries inside `insertRegistration`.) */
    suspend fun record(entry: AuditEntry)
}

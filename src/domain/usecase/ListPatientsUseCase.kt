package com.pms.dental.domain.usecase

import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.repository.PatientRepository

/** A page of search results plus the total count, so the client can render pagination. */
data class PatientPage(
    val patients: List<Patient>,
    val total: Long,
    val page: Int,
    val limit: Int,
)

/**
 * Business rule: list/search patients by name, paged. `includeInactive` defaults to false so
 * deactivated patients are hidden unless explicitly requested. Page and limit are clamped to
 * safe bounds (limit 1..100, page >= 1).
 */
class ListPatientsUseCase(private val patients: PatientRepository) {
    suspend operator fun invoke(
        query: String,
        page: Int,
        limit: Int,
        includeInactive: Boolean,
    ): PatientPage {
        val safeLimit = limit.coerceIn(1, MAX_LIMIT)
        val safePage = page.coerceAtLeast(1)
        val trimmed = query.trim()
        val offset = (safePage - 1) * safeLimit
        return PatientPage(
            patients = patients.search(trimmed, safeLimit, offset, includeInactive),
            total = patients.countSearch(trimmed, includeInactive),
            page = safePage,
            limit = safeLimit,
        )
    }

    private companion object {
        const val MAX_LIMIT = 100
    }
}

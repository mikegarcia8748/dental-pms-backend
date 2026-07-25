package com.pms.dental.domain.model

import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * A patient of the clinic, mirroring the PDA "Patient Information Record" (page 1).
 *
 * [registeredAt] is the clinically meaningful join date — **backdatable** for [isLegacy]
 * patients migrated from paper — while [createdAt] is the immutable row-insert time. Keeping
 * them separate lets a legacy record carry its real date without falsifying the audit trail.
 * [dateOfBirth] and [mobileNumber] are nullable so sparse legacy records can still be entered.
 */
data class Patient(
    val id: UUID,
    val lastName: String,
    val firstName: String,
    val middleName: String?,
    val suffix: String?,
    val nickname: String?,
    val dateOfBirth: LocalDate?,
    val sex: Sex,
    val religion: String?,
    val nationality: String?,
    val civilStatus: String?,
    val occupation: String?,
    val address: String?,
    val mobileNumber: String?,
    val homeNumber: String?,
    val officeNumber: String?,
    val email: String?,
    val guardianName: String?,
    val guardianRelationship: String?,
    val guardianOccupation: String?,
    val guardianContact: String?,
    val emergencyContactName: String?,
    val emergencyContactRelationship: String?,
    val emergencyContactNumber: String?,
    val isSenior: Boolean,
    val isPwd: Boolean,
    val scPwdIdNumber: String?,
    val tin: String?,
    val dentalInsurance: String?,
    val insuranceEffectiveDate: LocalDate?,
    val referralSource: String?,
    val isLegacy: Boolean,
    val legacySummary: String?,
    val registeredAt: Instant,
    val active: Boolean,
    val createdBy: UUID,
    val createdAt: Instant,
    val updatedBy: UUID?,
    val updatedAt: Instant?,
)

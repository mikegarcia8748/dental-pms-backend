@file:OptIn(ExperimentalUuidApi::class)

package com.pms.dental.data

import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestamp
import kotlin.uuid.ExperimentalUuidApi

/** Schema is owned by Flyway (`resources/db/migration`); these mirror it for type-safe queries. */

object AppUsers : Table("app_user") {
    val id = uuid("id")
    val email = varchar("email", 320)
    val displayName = varchar("display_name", 200)
    val role = varchar("role", 20)
    val active = bool("active")
    val passwordHash = varchar("password_hash", 100).nullable()
    val firebaseUid = varchar("firebase_uid", 128).nullable()
    val authSource = varchar("auth_source", 20)
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object RefreshTokens : Table("refresh_token") {
    val id = uuid("id")
    val userId = uuid("user_id").references(AppUsers.id)
    val tokenHash = varchar("token_hash", 64)
    val expiresAt = timestamp("expires_at")
    val revoked = bool("revoked")
    val createdAt = timestamp("created_at")
    override val primaryKey = PrimaryKey(id)
}

object Patients : Table("patient") {
    val id = uuid("id")
    val lastName = varchar("last_name", 100)
    val firstName = varchar("first_name", 100)
    val middleName = varchar("middle_name", 100).nullable()
    val suffix = varchar("suffix", 20).nullable()
    val nickname = varchar("nickname", 100).nullable()
    val dateOfBirth = date("date_of_birth").nullable()
    val sex = varchar("sex", 10)
    val religion = varchar("religion", 100).nullable()
    val nationality = varchar("nationality", 100).nullable()
    val civilStatus = varchar("civil_status", 30).nullable()
    val occupation = varchar("occupation", 150).nullable()
    val address = text("address").nullable()
    val mobileNumber = varchar("mobile_number", 40).nullable()
    val homeNumber = varchar("home_number", 40).nullable()
    val officeNumber = varchar("office_number", 40).nullable()
    val email = varchar("email", 320).nullable()
    val guardianName = varchar("guardian_name", 200).nullable()
    val guardianRelationship = varchar("guardian_relationship", 60).nullable()
    val guardianOccupation = varchar("guardian_occupation", 150).nullable()
    val guardianContact = varchar("guardian_contact", 40).nullable()
    val emergencyContactName = varchar("emergency_contact_name", 200).nullable()
    val emergencyContactRelationship = varchar("emergency_contact_relationship", 60).nullable()
    val emergencyContactNumber = varchar("emergency_contact_number", 40).nullable()
    val isSenior = bool("is_senior")
    val isPwd = bool("is_pwd")
    val scPwdIdNumber = varchar("sc_pwd_id_number", 60).nullable()
    val tin = varchar("tin", 30).nullable()
    val dentalInsurance = varchar("dental_insurance", 200).nullable()
    val insuranceEffectiveDate = date("insurance_effective_date").nullable()
    val referralSource = varchar("referral_source", 200).nullable()
    val isLegacy = bool("is_legacy")
    val legacySummary = text("legacy_summary").nullable()
    val registeredAt = timestamp("registered_at")
    val active = bool("active")
    val createdBy = uuid("created_by")
    val createdAt = timestamp("created_at")
    val updatedBy = uuid("updated_by").nullable()
    val updatedAt = timestamp("updated_at").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Allergies : Table("allergy") {
    val id = uuid("id")
    val patientId = uuid("patient_id")
    val substance = varchar("substance", 150)
    val severity = varchar("severity", 20).nullable()
    val note = text("note").nullable()
    val active = bool("active")
    val recordedBy = uuid("recorded_by")
    val recordedAt = timestamp("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

object IntakeQuestions : Table("intake_question") {
    val id = uuid("id")
    val section = varchar("section", 10)
    val code = varchar("code", 80)
    val prompt = text("prompt")
    val answerType = varchar("answer_type", 10)
    val choices = text("choices").nullable()
    val displayOrder = integer("display_order")
    val version = integer("version")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

object PatientIntakeAnswers : Table("patient_intake_answer") {
    val id = uuid("id")
    val patientId = uuid("patient_id")
    val questionId = uuid("question_id")
    val answerBoolean = bool("answer_boolean").nullable()
    val answerText = text("answer_text").nullable()
    val answerDate = date("answer_date").nullable()
    val recordedBy = uuid("recorded_by")
    val recordedAt = timestamp("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

object Consents : Table("consent") {
    val id = uuid("id")
    val patientId = uuid("patient_id")
    val type = varchar("type", 20)
    val textVersion = varchar("text_version", 40)
    val acknowledgedByRole = varchar("acknowledged_by_role", 20)
    val acknowledgedByName = varchar("acknowledged_by_name", 200).nullable()
    val acknowledgedAt = timestamp("acknowledged_at")
    val recordedBy = uuid("recorded_by")
    val recordedAt = timestamp("recorded_at")
    override val primaryKey = PrimaryKey(id)
}

object ConsentTexts : Table("consent_text") {
    val id = uuid("id")
    val type = varchar("type", 20)
    val version = varchar("version", 40)
    val title = varchar("title", 200)
    val body = text("body")
    val active = bool("active")
    override val primaryKey = PrimaryKey(id)
}

object AuditLogs : Table("audit_log") {
    val id = uuid("id")
    val userId = uuid("user_id")
    val action = varchar("action", 20)
    val entity = varchar("entity", 40)
    val entityId = uuid("entity_id")
    val at = timestamp("at")
    override val primaryKey = PrimaryKey(id)
}

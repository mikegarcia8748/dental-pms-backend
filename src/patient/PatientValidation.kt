package com.pms.dental.patient

import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.AllergySeverity
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.Sex
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Request-**shape** validation for the patient endpoints (an HTTP-adapter concern; the use cases
 * stay pure). Each returns a human message when the input is malformed, or `null` when it is fine;
 * the route turns a non-null result into a 400. Domain rules (minor→guardian, data-privacy consent,
 * answer/consent references) live in the use cases, not here.
 */

// ── parse helpers, shared with the DTO → domain mapping in PatientRoutes ─────────────────────
internal fun String.parseIsoDate(): LocalDate? = runCatching { LocalDate.parse(this) }.getOrNull()
internal fun String.parseIsoInstant(): Instant? = runCatching { Instant.parse(this) }.getOrNull()
internal fun String.parseUuidOrNull(): UUID? = runCatching { UUID.fromString(this) }.getOrNull()
internal inline fun <reified T : Enum<T>> String.parseEnumOrNull(): T? =
    runCatching { enumValueOf<T>(this) }.getOrNull()

private inline fun <reified T : Enum<T>> enumNames(): String = enumValues<T>().joinToString(", ") { it.name }

private const val MAX_NAME = 100
private const val MAX_EMAIL = 320

fun RegisterPatientRequest.validationError(): String? {
    profile.validationError()?.let { return it }
    allergies.forEach { it.validationError()?.let { msg -> return msg } }
    answers.forEach { it.validationError()?.let { msg -> return msg } }
    consents.forEach { it.validationError()?.let { msg -> return msg } }
    return null
}

fun UpsertAnswersRequest.validationError(): String? {
    answers.forEach { it.validationError()?.let { msg -> return msg } }
    return null
}

fun PatientProfileDto.validationError(): String? = when {
    lastName.isBlank() -> "lastName is required"
    firstName.isBlank() -> "firstName is required"
    lastName.length > MAX_NAME -> "lastName must be at most $MAX_NAME characters"
    firstName.length > MAX_NAME -> "firstName must be at most $MAX_NAME characters"
    sex.parseEnumOrNull<Sex>() == null -> "sex must be one of: ${enumNames<Sex>()}"
    dateOfBirth != null && dateOfBirth.parseIsoDate() == null -> "dateOfBirth must be an ISO date (yyyy-MM-dd)"
    insuranceEffectiveDate != null && insuranceEffectiveDate.parseIsoDate() == null ->
        "insuranceEffectiveDate must be an ISO date (yyyy-MM-dd)"
    registeredAt != null && registeredAt.parseIsoInstant() == null -> "registeredAt must be an ISO-8601 instant"
    email != null && email.length > MAX_EMAIL -> "email must be at most $MAX_EMAIL characters"
    else -> null
}

fun AllergyInput.validationError(): String? = when {
    substance.isBlank() -> "allergy substance is required"
    severity != null && severity.parseEnumOrNull<AllergySeverity>() == null ->
        "allergy severity must be one of: ${enumNames<AllergySeverity>()}"
    else -> null
}

fun IntakeAnswerInput.validationError(): String? = when {
    questionId.parseUuidOrNull() == null -> "answer questionId must be a UUID"
    answerDate != null && answerDate.parseIsoDate() == null -> "answer answerDate must be an ISO date (yyyy-MM-dd)"
    else -> null
}

fun ConsentInput.validationError(): String? = when {
    type.parseEnumOrNull<ConsentType>() == null -> "consent type must be one of: ${enumNames<ConsentType>()}"
    textVersion.isBlank() -> "consent textVersion is required"
    acknowledgedByRole.parseEnumOrNull<AcknowledgedByRole>() == null ->
        "consent acknowledgedByRole must be one of: ${enumNames<AcknowledgedByRole>()}"
    acknowledgedAt != null && acknowledgedAt.parseIsoInstant() == null ->
        "consent acknowledgedAt must be an ISO-8601 instant"
    else -> null
}

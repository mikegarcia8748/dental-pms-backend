package com.pms.dental.patient

import com.pms.dental.auth.ErrorResponse
import com.pms.dental.auth.authenticatedUser
import com.pms.dental.auth.authorize
import com.pms.dental.domain.model.AcknowledgedByRole
import com.pms.dental.domain.model.Allergy
import com.pms.dental.domain.model.AllergySeverity
import com.pms.dental.domain.model.Consent
import com.pms.dental.domain.model.ConsentText
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeQuestion
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.model.NewAllergy
import com.pms.dental.domain.model.NewAnswer
import com.pms.dental.domain.model.NewConsent
import com.pms.dental.domain.model.Patient
import com.pms.dental.domain.model.PatientDemographics
import com.pms.dental.domain.model.PatientDetails
import com.pms.dental.domain.model.PatientIntakeAnswer
import com.pms.dental.domain.model.PatientRegistration
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.model.Sex
import com.pms.dental.domain.usecase.AddAllergyResult
import com.pms.dental.domain.usecase.DeactivateAllergyResult
import com.pms.dental.domain.usecase.DeactivatePatientResult
import com.pms.dental.domain.usecase.PatientDetailsResult
import com.pms.dental.domain.usecase.PatientPage
import com.pms.dental.domain.usecase.ReactivatePatientResult
import com.pms.dental.domain.usecase.RecordConsentError
import com.pms.dental.domain.usecase.RecordConsentResult
import com.pms.dental.domain.usecase.RegisterPatientError
import com.pms.dental.domain.usecase.RegisterPatientResult
import com.pms.dental.domain.usecase.UpdateAllergyResult
import com.pms.dental.domain.usecase.UpdatePatientError
import com.pms.dental.domain.usecase.UpdatePatientResult
import com.pms.dental.domain.usecase.UpsertAnswersError
import com.pms.dental.domain.usecase.UpsertAnswersResult
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.route

/**
 * Patient registration & intake endpoints. Thin adapters: parse/validate the request, call the use
 * case, map its sealed result onto an HTTP status. All routes require the Dentist role. The acting
 * user's id (from the verified JWT) is threaded into every write for attribution/audit.
 */
fun Route.patientRoutes(uc: PatientUseCases) {
    route("/patients") {
        authorize(Role.DENTIST) {
            post({
                summary = "Register a patient"
                description = "Create a patient with nested allergies, intake answers, and consents in one transaction."
                tags("Patients")
                securitySchemeNames("bearerAuth")
                request { body<RegisterPatientRequest>() }
                response {
                    code(HttpStatusCode.Created) { description = "Registered; returns the full record."; body<PatientDetailsResponse>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid input or a rejected business rule."; body<ErrorResponse>() }
                }
            }) {
                val body = call.receive<RegisterPatientRequest>()
                val error = body.validationError()
                if (error != null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                } else {
                    val actingUserId = call.authenticatedUser!!.id
                    when (val result = uc.register(body.toCommand(), actingUserId)) {
                        is RegisterPatientResult.Success ->
                            call.respond(HttpStatusCode.Created, result.details.toResponse())
                        is RegisterPatientResult.Rejected ->
                            call.respond(HttpStatusCode.BadRequest, result.error.toErrorResponse())
                    }
                }
            }

            get({
                summary = "List / search patients"
                description = "Query params: q (name search), page (default 1), limit (default 20, max 100), includeInactive (default false)."
                tags("Patients")
                securitySchemeNames("bearerAuth")
                response {
                    code(HttpStatusCode.OK) { description = "A page of matching patients."; body<PatientPageResponse>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid query parameter."; body<ErrorResponse>() }
                }
            }) {
                val params = call.request.queryParameters
                val pageStr = params["page"]; val limitStr = params["limit"]; val inactiveStr = params["includeInactive"]
                val page = pageStr?.toIntOrNull()
                val limit = limitStr?.toIntOrNull()
                val includeInactive = inactiveStr?.toBooleanStrictOrNull()
                if ((pageStr != null && page == null) || (limitStr != null && limit == null) ||
                    (inactiveStr != null && includeInactive == null)
                ) {
                    call.respond(
                        HttpStatusCode.BadRequest,
                        ErrorResponse("invalid_request", "page and limit must be integers; includeInactive must be true or false"),
                    )
                } else {
                    call.respond(
                        HttpStatusCode.OK,
                        uc.list(params["q"].orEmpty(), page ?: 1, limit ?: 20, includeInactive ?: false).toResponse(),
                    )
                }
            }

            route("/{id}") {
                get({
                    summary = "Get a patient's full record"
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.OK) { description = "The patient with allergies, answers, and consents."; body<PatientDetailsResponse>() }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@get call.respond(HttpStatusCode.NotFound, notFound())
                    when (val result = uc.details(patientId)) {
                        is PatientDetailsResult.Found -> call.respond(HttpStatusCode.OK, result.details.toResponse())
                        PatientDetailsResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                    }
                }

                put({
                    summary = "Update a patient's profile"
                    description = "Replaces the demographic / PH / legacy fields."
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    request { body<PatientProfileDto>() }
                    response {
                        code(HttpStatusCode.OK) { description = "Updated patient."; body<PatientResponse>() }
                        code(HttpStatusCode.BadRequest) { description = "Invalid input or rejected rule."; body<ErrorResponse>() }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                        code(HttpStatusCode.Conflict) { description = "Patient is deactivated."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@put call.respond(HttpStatusCode.NotFound, notFound())
                    val body = call.receive<PatientProfileDto>()
                    val error = body.validationError()
                    if (error != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                    } else {
                        val actingUserId = call.authenticatedUser!!.id
                        when (val result = uc.update(patientId, body.toDemographics(), actingUserId)) {
                            is UpdatePatientResult.Success -> call.respond(HttpStatusCode.OK, result.patient.toResponse())
                            is UpdatePatientResult.Rejected -> when (result.error) {
                                UpdatePatientError.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                                UpdatePatientError.PatientInactive -> call.respond(HttpStatusCode.Conflict, inactive())
                                UpdatePatientError.MinorRequiresGuardian ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("minor_requires_guardian", "A patient under 18 requires a guardian name and contact"))
                                UpdatePatientError.FutureDateOfBirth ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("future_date_of_birth", "Date of birth cannot be in the future"))
                                UpdatePatientError.FutureRegistrationDate ->
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("future_registration_date", "Registration date cannot be in the future"))
                            }
                        }
                    }
                }

                post("/deactivate", {
                    summary = "Deactivate (soft-delete) a patient"
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.NoContent) { description = "Patient is inactive." }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                    when (uc.deactivate(patientId, call.authenticatedUser!!.id)) {
                        DeactivatePatientResult.Success -> call.respond(HttpStatusCode.NoContent)
                        DeactivatePatientResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                    }
                }

                post("/reactivate", {
                    summary = "Reactivate a soft-deleted patient"
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    response {
                        code(HttpStatusCode.NoContent) { description = "Patient is active." }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                    when (uc.reactivate(patientId, call.authenticatedUser!!.id)) {
                        ReactivatePatientResult.Success -> call.respond(HttpStatusCode.NoContent)
                        ReactivatePatientResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                    }
                }

                route("/allergies") {
                    post({
                        summary = "Add an allergy"
                        tags("Patients")
                        securitySchemeNames("bearerAuth")
                        request { body<AllergyInput>() }
                        response {
                            code(HttpStatusCode.Created) { description = "Added."; body<AllergyResponse>() }
                            code(HttpStatusCode.BadRequest) { description = "Invalid input."; body<ErrorResponse>() }
                            code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                            code(HttpStatusCode.Conflict) { description = "Patient is deactivated."; body<ErrorResponse>() }
                        }
                    }) {
                        val patientId = call.parameters["id"]?.parseUuidOrNull()
                            ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                        val body = call.receive<AllergyInput>()
                        val error = body.validationError()
                        if (error != null) {
                            call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                        } else when (val result = uc.addAllergy(patientId, body.toNewAllergy(), call.authenticatedUser!!.id)) {
                            is AddAllergyResult.Success -> call.respond(HttpStatusCode.Created, result.allergy.toResponse())
                            AddAllergyResult.PatientNotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                            AddAllergyResult.PatientInactive -> call.respond(HttpStatusCode.Conflict, inactive())
                        }
                    }

                    route("/{allergyId}") {
                        put({
                            summary = "Update an allergy"
                            tags("Patients")
                            securitySchemeNames("bearerAuth")
                            request { body<AllergyInput>() }
                            response {
                                code(HttpStatusCode.OK) { description = "Updated."; body<AllergyResponse>() }
                                code(HttpStatusCode.BadRequest) { description = "Invalid input."; body<ErrorResponse>() }
                                code(HttpStatusCode.NotFound) { description = "No such allergy for this patient."; body<ErrorResponse>() }
                            }
                        }) {
                            val patientId = call.parameters["id"]?.parseUuidOrNull()
                            val allergyId = call.parameters["allergyId"]?.parseUuidOrNull()
                            if (patientId == null || allergyId == null) {
                                call.respond(HttpStatusCode.NotFound, notFound())
                            } else {
                                val body = call.receive<AllergyInput>()
                                val error = body.validationError()
                                if (error != null) {
                                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                                } else when (val result = uc.updateAllergy(patientId, allergyId, body.toNewAllergy(), call.authenticatedUser!!.id)) {
                                    is UpdateAllergyResult.Success -> call.respond(HttpStatusCode.OK, result.allergy.toResponse())
                                    UpdateAllergyResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                                }
                            }
                        }

                        post("/deactivate", {
                            summary = "Deactivate (soft-delete) an allergy"
                            tags("Patients")
                            securitySchemeNames("bearerAuth")
                            response {
                                code(HttpStatusCode.NoContent) { description = "Allergy is inactive." }
                                code(HttpStatusCode.NotFound) { description = "No such allergy for this patient."; body<ErrorResponse>() }
                            }
                        }) {
                            val patientId = call.parameters["id"]?.parseUuidOrNull()
                            val allergyId = call.parameters["allergyId"]?.parseUuidOrNull()
                            if (patientId == null || allergyId == null) {
                                call.respond(HttpStatusCode.NotFound, notFound())
                            } else when (uc.deactivateAllergy(patientId, allergyId, call.authenticatedUser!!.id)) {
                                DeactivateAllergyResult.Success -> call.respond(HttpStatusCode.NoContent)
                                DeactivateAllergyResult.NotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                            }
                        }
                    }
                }

                put("/intake-answers", {
                    summary = "Set / replace intake answers"
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    request { body<UpsertAnswersRequest>() }
                    response {
                        code(HttpStatusCode.OK) { description = "The stored answers."; body<List<IntakeAnswerResponse>>() }
                        code(HttpStatusCode.BadRequest) { description = "Invalid input or rejected rule."; body<ErrorResponse>() }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                        code(HttpStatusCode.Conflict) { description = "Patient is deactivated."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@put call.respond(HttpStatusCode.NotFound, notFound())
                    val body = call.receive<UpsertAnswersRequest>()
                    val error = body.validationError()
                    if (error != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                    } else when (val result = uc.upsertAnswers(patientId, body.answers.map { it.toNewAnswer() }, call.authenticatedUser!!.id)) {
                        is UpsertAnswersResult.Success -> call.respond(HttpStatusCode.OK, result.answers.map { it.toResponse() })
                        is UpsertAnswersResult.Rejected -> when (result.error) {
                            UpsertAnswersError.PatientNotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                            UpsertAnswersError.PatientInactive -> call.respond(HttpStatusCode.Conflict, inactive())
                            UpsertAnswersError.UnknownQuestion ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("unknown_question", "An answer references an unknown or inactive question"))
                            UpsertAnswersError.AnswerTypeMismatch ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("answer_type_mismatch", "An answer value does not match its question's type"))
                            UpsertAnswersError.AnswerNotInChoices ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("answer_not_in_choices", "A choice answer is not one of the question's allowed choices"))
                        }
                    }
                }

                post("/consents", {
                    summary = "Record a consent acknowledgment"
                    tags("Patients")
                    securitySchemeNames("bearerAuth")
                    request { body<ConsentInput>() }
                    response {
                        code(HttpStatusCode.Created) { description = "Recorded."; body<ConsentResponse>() }
                        code(HttpStatusCode.BadRequest) { description = "Invalid input or unknown consent text."; body<ErrorResponse>() }
                        code(HttpStatusCode.NotFound) { description = "No such patient."; body<ErrorResponse>() }
                        code(HttpStatusCode.Conflict) { description = "Patient is deactivated."; body<ErrorResponse>() }
                    }
                }) {
                    val patientId = call.parameters["id"]?.parseUuidOrNull()
                        ?: return@post call.respond(HttpStatusCode.NotFound, notFound())
                    val body = call.receive<ConsentInput>()
                    val error = body.validationError()
                    if (error != null) {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", error))
                    } else when (val result = uc.recordConsent(patientId, body.toNewConsent(), call.authenticatedUser!!.id)) {
                        is RecordConsentResult.Success -> call.respond(HttpStatusCode.Created, result.consent.toResponse())
                        is RecordConsentResult.Rejected -> when (result.error) {
                            RecordConsentError.PatientNotFound -> call.respond(HttpStatusCode.NotFound, notFound())
                            RecordConsentError.PatientInactive -> call.respond(HttpStatusCode.Conflict, inactive())
                            RecordConsentError.UnknownConsentText ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("unknown_consent_text", "A consent references an unknown or inactive consent-text version"))
                            RecordConsentError.FutureAcknowledgmentDate ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("future_acknowledgment_date", "Consent cannot be acknowledged in the future"))
                            RecordConsentError.MinorConsentRequiresGuardian ->
                                call.respond(HttpStatusCode.BadRequest, ErrorResponse("minor_consent_requires_guardian", "A minor's consent must be acknowledged by a guardian"))
                        }
                    }
                }
            }
        }
    }

    route("/intake-questions") {
        authorize(Role.DENTIST) {
            get({
                summary = "List active intake questions"
                description = "Optional query param: section = MEDICAL | DENTAL. Returns questions in display order."
                tags("Patients")
                securitySchemeNames("bearerAuth")
                response {
                    code(HttpStatusCode.OK) { description = "The active question set."; body<List<IntakeQuestionResponse>>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid section."; body<ErrorResponse>() }
                }
            }) {
                val sectionParam = call.request.queryParameters["section"]
                val section = sectionParam?.parseEnumOrNull<IntakeSection>()
                if (sectionParam != null && section == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "section must be one of: MEDICAL, DENTAL"))
                } else {
                    call.respond(HttpStatusCode.OK, uc.listQuestions(section).map { it.toResponse() })
                }
            }
        }
    }

    route("/consent-texts") {
        authorize(Role.DENTIST) {
            get({
                summary = "List active consent texts"
                description = "Optional query param: type = TREATMENT | RADIOGRAPH | EXTRACTION | DATA_PRIVACY."
                tags("Patients")
                securitySchemeNames("bearerAuth")
                response {
                    code(HttpStatusCode.OK) { description = "The active consent texts."; body<List<ConsentTextResponse>>() }
                    code(HttpStatusCode.BadRequest) { description = "Invalid type."; body<ErrorResponse>() }
                }
            }) {
                val typeParam = call.request.queryParameters["type"]
                val type = typeParam?.parseEnumOrNull<ConsentType>()
                if (typeParam != null && type == null) {
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse("invalid_request", "type must be one of: TREATMENT, RADIOGRAPH, EXTRACTION, DATA_PRIVACY"))
                } else {
                    call.respond(HttpStatusCode.OK, uc.listConsentTexts(type).map { it.toResponse() })
                }
            }
        }
    }
}

private fun notFound() = ErrorResponse("not_found", "No such patient")
private fun inactive() = ErrorResponse("patient_inactive", "The patient is deactivated; reactivate before editing")

private fun RegisterPatientError.toErrorResponse(): ErrorResponse = when (this) {
    RegisterPatientError.MinorRequiresGuardian ->
        ErrorResponse("minor_requires_guardian", "A patient under 18 requires a guardian name and contact")
    RegisterPatientError.MissingDataPrivacyConsent ->
        ErrorResponse("missing_data_privacy_consent", "A data-privacy consent is required to register a non-legacy patient")
    RegisterPatientError.UnknownQuestion ->
        ErrorResponse("unknown_question", "An answer references an unknown or inactive question")
    RegisterPatientError.AnswerTypeMismatch ->
        ErrorResponse("answer_type_mismatch", "An answer value does not match its question's type")
    RegisterPatientError.AnswerNotInChoices ->
        ErrorResponse("answer_not_in_choices", "A choice answer is not one of the question's allowed choices")
    RegisterPatientError.UnknownConsentText ->
        ErrorResponse("unknown_consent_text", "A consent references an unknown or inactive consent-text version")
    RegisterPatientError.MinorConsentRequiresGuardian ->
        ErrorResponse("minor_consent_requires_guardian", "A minor's consent must be acknowledged by a guardian")
    RegisterPatientError.FutureDateOfBirth ->
        ErrorResponse("future_date_of_birth", "Date of birth cannot be in the future")
    RegisterPatientError.FutureRegistrationDate ->
        ErrorResponse("future_registration_date", "Registration date cannot be in the future")
}

// ── DTO → domain ────────────────────────────────────────────────────────────────────────────
// INVARIANT: every route calls `validationError()` and returns 400 *before* mapping, so the `!!`
// on `parseEnumOrNull`/`parseUuidOrNull` below can never fail. Any new route must validate first.
private fun RegisterPatientRequest.toCommand() = PatientRegistration(
    demographics = profile.toDemographics(),
    allergies = allergies.map { it.toNewAllergy() },
    answers = answers.map { it.toNewAnswer() },
    consents = consents.map { it.toNewConsent() },
)

private fun PatientProfileDto.toDemographics() = PatientDemographics(
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
    suffix = suffix,
    nickname = nickname,
    dateOfBirth = dateOfBirth?.parseIsoDate(),
    sex = sex.parseEnumOrNull<Sex>()!!,
    religion = religion,
    nationality = nationality,
    civilStatus = civilStatus,
    occupation = occupation,
    address = address,
    mobileNumber = mobileNumber,
    homeNumber = homeNumber,
    officeNumber = officeNumber,
    email = email,
    guardianName = guardianName,
    guardianRelationship = guardianRelationship,
    guardianOccupation = guardianOccupation,
    guardianContact = guardianContact,
    emergencyContactName = emergencyContactName,
    emergencyContactRelationship = emergencyContactRelationship,
    emergencyContactNumber = emergencyContactNumber,
    isSenior = isSenior,
    isPwd = isPwd,
    scPwdIdNumber = scPwdIdNumber,
    tin = tin,
    dentalInsurance = dentalInsurance,
    insuranceEffectiveDate = insuranceEffectiveDate?.parseIsoDate(),
    referralSource = referralSource,
    isLegacy = isLegacy,
    legacySummary = legacySummary,
    registeredAt = registeredAt?.parseIsoInstant(),
)

private fun AllergyInput.toNewAllergy() = NewAllergy(substance, severity?.parseEnumOrNull<AllergySeverity>(), note)

private fun IntakeAnswerInput.toNewAnswer() =
    NewAnswer(questionId.parseUuidOrNull()!!, answerBoolean, answerText, answerDate?.parseIsoDate())

private fun ConsentInput.toNewConsent() = NewConsent(
    type = type.parseEnumOrNull<ConsentType>()!!,
    textVersion = textVersion,
    acknowledgedByRole = acknowledgedByRole.parseEnumOrNull<AcknowledgedByRole>()!!,
    acknowledgedByName = acknowledgedByName,
    acknowledgedAt = acknowledgedAt?.parseIsoInstant(),
)

// ── domain → DTO ────────────────────────────────────────────────────────────────────────────
private fun Patient.toResponse() = PatientResponse(
    id = id.toString(),
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
    suffix = suffix,
    nickname = nickname,
    dateOfBirth = dateOfBirth?.toString(),
    sex = sex.name,
    religion = religion,
    nationality = nationality,
    civilStatus = civilStatus,
    occupation = occupation,
    address = address,
    mobileNumber = mobileNumber,
    homeNumber = homeNumber,
    officeNumber = officeNumber,
    email = email,
    guardianName = guardianName,
    guardianRelationship = guardianRelationship,
    guardianOccupation = guardianOccupation,
    guardianContact = guardianContact,
    emergencyContactName = emergencyContactName,
    emergencyContactRelationship = emergencyContactRelationship,
    emergencyContactNumber = emergencyContactNumber,
    isSenior = isSenior,
    isPwd = isPwd,
    scPwdIdNumber = scPwdIdNumber,
    tin = tin,
    dentalInsurance = dentalInsurance,
    insuranceEffectiveDate = insuranceEffectiveDate?.toString(),
    referralSource = referralSource,
    isLegacy = isLegacy,
    legacySummary = legacySummary,
    registeredAt = registeredAt.toString(),
    active = active,
    createdAt = createdAt.toString(),
    updatedAt = updatedAt?.toString(),
)

private fun Patient.toSummary() = PatientSummaryResponse(
    id = id.toString(),
    lastName = lastName,
    firstName = firstName,
    middleName = middleName,
    dateOfBirth = dateOfBirth?.toString(),
    mobileNumber = mobileNumber,
    isSenior = isSenior,
    isPwd = isPwd,
    isLegacy = isLegacy,
    active = active,
    registeredAt = registeredAt.toString(),
)

private fun Allergy.toResponse() = AllergyResponse(id.toString(), substance, severity?.name, note, active)

private fun PatientIntakeAnswer.toResponse() =
    IntakeAnswerResponse(id.toString(), questionId.toString(), answerBoolean, answerText, answerDate?.toString())

private fun Consent.toResponse() =
    ConsentResponse(id.toString(), type.name, textVersion, acknowledgedByRole.name, acknowledgedByName, acknowledgedAt.toString())

private fun PatientDetails.toResponse() = PatientDetailsResponse(
    patient = patient.toResponse(),
    allergies = allergies.map { it.toResponse() },
    answers = answers.map { it.toResponse() },
    consents = consents.map { it.toResponse() },
)

private fun PatientPage.toResponse() = PatientPageResponse(patients.map { it.toSummary() }, total, page, limit)

private fun IntakeQuestion.toResponse() =
    IntakeQuestionResponse(id.toString(), section.name, code, prompt, answerType.name, choices, displayOrder)

private fun ConsentText.toResponse() = ConsentTextResponse(id.toString(), type.name, version, title, body)

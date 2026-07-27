package com.pms.dental.patient

import com.pms.dental.auth.configureSecurity
import com.pms.dental.config.AuthConfig
import com.pms.dental.configureSerialization
import com.pms.dental.configureStatusPages
import com.pms.dental.domain.model.AppUser
import com.pms.dental.domain.model.ConsentType
import com.pms.dental.domain.model.IntakeAnswerType
import com.pms.dental.domain.model.IntakeSection
import com.pms.dental.domain.model.Role
import com.pms.dental.domain.service.Clock
import com.pms.dental.domain.usecase.AddAllergyUseCase
import com.pms.dental.domain.usecase.DeactivateAllergyUseCase
import com.pms.dental.domain.usecase.DeactivatePatientUseCase
import com.pms.dental.domain.usecase.GetPatientDetailsUseCase
import com.pms.dental.domain.usecase.ListConsentTextsUseCase
import com.pms.dental.domain.usecase.ListIntakeQuestionsUseCase
import com.pms.dental.domain.usecase.ListPatientsUseCase
import com.pms.dental.domain.usecase.ReactivatePatientUseCase
import com.pms.dental.domain.usecase.RecordConsentUseCase
import com.pms.dental.domain.usecase.RegisterPatientUseCase
import com.pms.dental.domain.usecase.UpdateAllergyUseCase
import com.pms.dental.domain.usecase.UpdatePatientUseCase
import com.pms.dental.domain.usecase.UpsertIntakeAnswersUseCase
import com.pms.dental.infra.JwtAccessTokenIssuer
import com.pms.dental.infra.UuidGenerator
import com.pms.dental.support.FakeAllergyRepository
import com.pms.dental.support.FakeAuditLogRepository
import com.pms.dental.support.FakeConsentRepository
import com.pms.dental.support.FakeConsentTextRepository
import com.pms.dental.support.FakeIntakeQuestionRepository
import com.pms.dental.support.FakePatientIntakeAnswerRepository
import com.pms.dental.support.FakePatientRepository
import com.pms.dental.support.consentText
import com.pms.dental.support.question
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import java.time.Instant
import java.util.UUID

private class Wiring {
    val config = AuthConfig(
        jwtSecret = "test-secret-that-is-at-least-32-characters!",
        jwtIssuer = "dental-pms",
        jwtAudience = "dental-pms-web",
        accessTtlMinutes = 15,
        refreshTtlDays = 14,
        bootstrapAccounts = emptyList(),
    )
    private val clock = Clock { Instant.now() }
    private val issuer = JwtAccessTokenIssuer(config.jwtSecret, config.jwtIssuer, config.jwtAudience, config.accessTtlSeconds, clock)
    private val ids = UuidGenerator()

    val patients = FakePatientRepository()
    val allergies = FakeAllergyRepository()
    val questions = FakeIntakeQuestionRepository()
    val answers = FakePatientIntakeAnswerRepository()
    val consents = FakeConsentRepository()
    val consentTexts = FakeConsentTextRepository()
    val audit = FakeAuditLogRepository()

    val useCases = PatientUseCases(
        register = RegisterPatientUseCase(patients, questions, consentTexts, clock, ids),
        list = ListPatientsUseCase(patients),
        details = GetPatientDetailsUseCase(patients, allergies, answers, consents),
        update = UpdatePatientUseCase(patients, audit, clock, ids),
        deactivate = DeactivatePatientUseCase(patients, audit, clock, ids),
        reactivate = ReactivatePatientUseCase(patients, audit, clock, ids),
        addAllergy = AddAllergyUseCase(patients, allergies, audit, clock, ids),
        updateAllergy = UpdateAllergyUseCase(allergies, audit, clock, ids),
        deactivateAllergy = DeactivateAllergyUseCase(allergies, audit, clock, ids),
        upsertAnswers = UpsertIntakeAnswersUseCase(patients, questions, answers, audit, clock, ids),
        recordConsent = RecordConsentUseCase(patients, consentTexts, consents, audit, clock, ids),
        listQuestions = ListIntakeQuestionsUseCase(questions),
        listConsentTexts = ListConsentTextsUseCase(consentTexts),
    )

    private val dentist = AppUser(UUID.randomUUID(), "dentist@clinic.test", "Dr. Molar", Role.DENTIST, true, "hash")
    private val sysadmin = AppUser(UUID.randomUUID(), "admin@clinic.test", "Admin", Role.SYSADMIN, true, "hash")

    fun dentistToken(): String = issuer.issue(dentist).token
    fun sysadminToken(): String = issuer.issue(sysadmin).token
}

private fun ApplicationTestBuilder.installApp(w: Wiring) {
    application {
        configureSerialization()
        configureStatusPages()
        configureSecurity(w.config)
        routing { patientRoutes(w.useCases) }
    }
}

private fun ApplicationTestBuilder.jsonClient() =
    createClient { install(ClientContentNegotiation) { json() } }

private fun validRegistration(questionId: UUID) = RegisterPatientRequest(
    profile = PatientProfileDto(
        lastName = "Dela Cruz", firstName = "Juan", sex = "MALE",
        dateOfBirth = "1990-01-01", mobileNumber = "09170000000",
    ),
    allergies = listOf(AllergyInput("Penicillin", "SEVERE", null)),
    answers = listOf(IntakeAnswerInput(questionId.toString(), answerBoolean = true)),
    consents = listOf(ConsentInput("DATA_PRIVACY", "RA10173-v1", "PATIENT")),
)

class PatientRoutesTest : FunSpec({

    test("POST /patients - without a token - returns 401") {
        testApplication {
            installApp(Wiring())
            val client = jsonClient()

            val response = client.post("/patients") {
                contentType(ContentType.Application.Json)
                setBody(validRegistration(UUID.randomUUID()))
            }

            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /patients - with a SYSADMIN token - returns 403") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.sysadminToken())
                contentType(ContentType.Application.Json)
                setBody(validRegistration(UUID.randomUUID()))
            }

            response.status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("POST /patients - valid full intake as dentist - returns 201 with the nested record") {
        val w = Wiring()
        val questionId = UUID.randomUUID()
        w.questions.seed(question("good_health", IntakeAnswerType.BOOLEAN, id = questionId))
        w.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(validRegistration(questionId))
            }

            response.status shouldBe HttpStatusCode.Created
            val body = response.body<PatientDetailsResponse>()
            body.patient.lastName shouldBe "Dela Cruz"
            body.allergies.size shouldBe 1
            body.answers.size shouldBe 1
            body.consents.size shouldBe 1
        }
    }

    test("POST /patients - non-legacy without data-privacy consent - returns 400 missing_data_privacy_consent") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterPatientRequest(
                        profile = PatientProfileDto(
                            lastName = "Reyes", firstName = "Ana", sex = "FEMALE",
                            dateOfBirth = "1988-03-02", mobileNumber = "09170000001",
                        ),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<com.pms.dental.auth.ErrorResponse>().error shouldBe "missing_data_privacy_consent"
        }
    }

    test("POST /patients - malformed JSON body as dentist - returns 400 not 500") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody("{ not valid json")
            }

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("GET /patients/{id} - unknown id - returns 404") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.get("/patients/${UUID.randomUUID()}") { bearerAuth(w.dentistToken()) }

            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("register then search - GET /patients finds the new patient by name") {
        val w = Wiring()
        val questionId = UUID.randomUUID()
        w.questions.seed(question("good_health", IntakeAnswerType.BOOLEAN, id = questionId))
        w.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        testApplication {
            installApp(w)
            val client = jsonClient()

            client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(validRegistration(questionId))
            }.status shouldBe HttpStatusCode.Created

            val page = client.get("/patients?q=cruz") { bearerAuth(w.dentistToken()) }.body<PatientPageResponse>()
            page.total shouldBe 1
            page.patients.single().lastName shouldBe "Dela Cruz"
        }
    }

    test("GET /intake-questions?section=DENTAL - returns only the seeded dental questions") {
        val w = Wiring()
        w.questions.seed(question("previous_dentist", IntakeAnswerType.TEXT, IntakeSection.DENTAL, displayOrder = 1))
        w.questions.seed(question("good_health", IntakeAnswerType.BOOLEAN, IntakeSection.MEDICAL, displayOrder = 1))
        testApplication {
            installApp(w)
            val client = jsonClient()

            val questions = client.get("/intake-questions?section=DENTAL") { bearerAuth(w.dentistToken()) }
                .body<List<IntakeQuestionResponse>>()

            questions.single().code shouldBe "previous_dentist"
        }
    }

    test("POST /patients - non-legacy without mobileNumber - returns 400 invalid_request") {
        val w = Wiring()
        w.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterPatientRequest(
                        profile = PatientProfileDto(lastName = "Reyes", firstName = "Ana", sex = "FEMALE", dateOfBirth = "1988-03-02"),
                        consents = listOf(ConsentInput("DATA_PRIVACY", "RA10173-v1", "PATIENT")),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<com.pms.dental.auth.ErrorResponse>().error shouldBe "invalid_request"
        }
    }

    test("POST /patients - duplicate questionId - returns 400 invalid_request") {
        val w = Wiring()
        val qid = UUID.randomUUID()
        w.questions.seed(question("good_health", IntakeAnswerType.BOOLEAN, id = qid))
        w.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        testApplication {
            installApp(w)
            val client = jsonClient()

            val response = client.post("/patients") {
                bearerAuth(w.dentistToken())
                contentType(ContentType.Application.Json)
                setBody(
                    RegisterPatientRequest(
                        profile = PatientProfileDto(
                            lastName = "Dela Cruz", firstName = "Juan", sex = "MALE",
                            dateOfBirth = "1990-01-01", mobileNumber = "09170000000",
                        ),
                        answers = listOf(
                            IntakeAnswerInput(qid.toString(), answerBoolean = true),
                            IntakeAnswerInput(qid.toString(), answerBoolean = false),
                        ),
                        consents = listOf(ConsentInput("DATA_PRIVACY", "RA10173-v1", "PATIENT")),
                    ),
                )
            }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<com.pms.dental.auth.ErrorResponse>().error shouldBe "invalid_request"
        }
    }

    test("GET /patients - non-integer limit - returns 400") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            client.get("/patients?limit=abc") { bearerAuth(w.dentistToken()) }.status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /patients/{id}/reactivate - unknown id - returns 404") {
        val w = Wiring()
        testApplication {
            installApp(w)
            val client = jsonClient()

            client.post("/patients/${UUID.randomUUID()}/reactivate") { bearerAuth(w.dentistToken()) }
                .status shouldBe HttpStatusCode.NotFound
        }
    }

    test("deactivated patient rejects edits with 409; reactivate restores editing") {
        val w = Wiring()
        val qid = UUID.randomUUID()
        w.questions.seed(question("good_health", IntakeAnswerType.BOOLEAN, id = qid))
        w.consentTexts.seed(consentText(ConsentType.DATA_PRIVACY, "RA10173-v1"))
        testApplication {
            installApp(w)
            val client = jsonClient()

            val id = client.post("/patients") {
                bearerAuth(w.dentistToken()); contentType(ContentType.Application.Json); setBody(validRegistration(qid))
            }.body<PatientDetailsResponse>().patient.id

            client.post("/patients/$id/deactivate") { bearerAuth(w.dentistToken()) }.status shouldBe HttpStatusCode.NoContent

            val blocked = client.post("/patients/$id/allergies") {
                bearerAuth(w.dentistToken()); contentType(ContentType.Application.Json); setBody(AllergyInput("Latex", "MILD", null))
            }
            blocked.status shouldBe HttpStatusCode.Conflict
            blocked.body<com.pms.dental.auth.ErrorResponse>().error shouldBe "patient_inactive"

            client.post("/patients/$id/reactivate") { bearerAuth(w.dentistToken()) }.status shouldBe HttpStatusCode.NoContent

            client.post("/patients/$id/allergies") {
                bearerAuth(w.dentistToken()); contentType(ContentType.Application.Json); setBody(AllergyInput("Latex", "MILD", null))
            }.status shouldBe HttpStatusCode.Created
        }
    }
})

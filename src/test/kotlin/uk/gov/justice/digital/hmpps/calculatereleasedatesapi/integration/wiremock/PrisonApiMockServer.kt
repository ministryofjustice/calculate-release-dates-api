package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.MappingBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ParameterContext
import org.junit.jupiter.api.extension.ParameterResolver
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CaseLoadType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CaseLoad
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

/*
    This class mocks the prison-api.
    JSON files exist in 'src/test/resources/test_data/api_integration
    Add files to any sub-directory to automatically stub rest calls. The file name will act as the prisoner id.
    The hashcode of the prisoner id string, will be the booking id.
    Once a file is added to any of the directories, all calls will be stubbed, if not all directories have an
    entry for the given prisoner id, the mock will fallback to a default json file.
 */
class PrisonApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback, ParameterResolver {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
    const val DEFAULT = "default"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
    private val DEFAULT_CASELOAD = CaseLoad("ABC", "ABC", CaseLoadType.INST, null, currentlyActive = true)
  }

  private val jsonTransformation = JsonTransformation()
  private val objectMapper = TestUtil.objectMapper()
  private val mockPrisonService = MockPrisonService(prisonApi, objectMapper, jsonTransformation)

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()

    val prisoners = jsonTransformation.getAllPrisonerDetails()
    val defaultPrisoner = prisoners[DEFAULT]!!

    val adjustments = jsonTransformation.getAllPrisonApiAdjustments()
    val defaultAdjustment = adjustments[DEFAULT]!!

    val sentences = jsonTransformation.getAllSentenceAndOffencesJson()
    val defaultSentence = sentences[DEFAULT]!!

    val finePayments = jsonTransformation.getAllOffenderFinePaymentsJson()
    val defaultFinePayment = finePayments[DEFAULT]!!

    val externalMovements = jsonTransformation.getAllExternalMovementsJson()
    val defaultExternalMovements = externalMovements[DEFAULT]!!

    val returnToCustodyDates = jsonTransformation.getAllReturnToCustodyDatesJson()

    val allPrisoners = jsonTransformation.getAllIntegrationPrisonerNames().distinct()
    allPrisoners.forEach {
      val prisoner = if (prisoners.containsKey(it)) {
        log.info("Stubbing prisoner details prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        // There is a matching json for the prisoner
        prisoners[it]!!.copy(bookingId = it.hashCode().toLong(), offenderNo = it)
      } else {
        log.info("Stubbing prisoner details prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
        // There is no matching json for the prisoner for this adjustment/offence. Use the generic prisoner
        defaultPrisoner.copy(bookingId = it.hashCode().toLong(), offenderNo = it)
      }
      prisonApi.stubGetPrisonerDetails(it, objectMapper.writeValueAsString(prisoner))

      val adjustment = if (adjustments.containsKey(it)) {
        log.info("Stubbing prison api adjustments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        adjustments[it]!!
      } else {
        log.info("Stubbing prison api adjustments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
        defaultAdjustment
      }
      prisonApi.stubGetSentenceAdjustments(it.hashCode().toLong(), adjustment)

      val sentence = if (sentences.containsKey(it)) {
        log.info("Stubbing sentences prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        sentences[it]!!
      } else {
        log.info("Stubbing sentences prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
        defaultSentence
      }
      prisonApi.stubGetSentencesAndOffences(it.hashCode().toLong(), sentence)

      if (returnToCustodyDates.containsKey(it)) {
        log.info("Stubbing return to custody prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        prisonApi.stubGetReturnToCustody(it.hashCode().toLong(), returnToCustodyDates[it]!!)
      } else {
        log.info("No return to custody to stub for prisonerId $it, bookingId ${it.hashCode().toLong()}")
        prisonApi.stubGetReturnToCustodyNotFound(it.hashCode().toLong())
      }

      val finePayment = if (finePayments.containsKey(it)) {
        log.info("Stubbing offender fine payments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        finePayments[it]!!
      } else {
        log.info("Stubbing offender fine payments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
        defaultFinePayment
      }
      prisonApi.stubOffenderFinePayments(it.hashCode().toLong(), finePayment)

      val externalMovement = if (externalMovements.containsKey(it)) {
        log.info("Stubbing external movements prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        externalMovements[it]!!
      } else {
        log.info("Stubbing external movements prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
        defaultExternalMovements
      }
      prisonApi.stubExternalMovements(it, externalMovement)

      prisonApi.stubPostOffenderDates(it.hashCode().toLong())

      val prisonCalculablePrisonersJson = jsonTransformation.getAllPrisonCalculablePrisonersJson()

      prisonCalculablePrisonersJson.forEach { (key, value) ->
        prisonApi.stubPrisonCalculablePrisoners(key, value)
      }
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    mockPrisonService
      .withCaseLoadsForMe(DEFAULT_CASELOAD, CaseLoad("PRIS", "PRIS", CaseLoadType.INST, null, currentlyActive = true))
    prisonApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonApi.stop()
  }

  override fun supportsParameter(parameterContext: ParameterContext, context: ExtensionContext): Boolean {
    return parameterContext.parameter.type == MockPrisonService::class.java
  }

  override fun resolveParameter(parameterContext: ParameterContext, context: ExtensionContext): Any {
    return MockPrisonService(prisonApi, objectMapper, jsonTransformation)
  }
}

class MockPrisonService(
  private val prisonApi: PrisonApiMockServer,
  private val objectMapper: ObjectMapper,
  private val jsonTransformation: JsonTransformation,
) {
  fun withCaseLoadsForMe(vararg caseloads: CaseLoad): MockPrisonService {
    prisonApi.stubCaseloads(objectMapper.writeValueAsString(caseloads.toList()))
    return this
  }

  fun withInstAgencies(agencies: List<Agency>): MockPrisonService {
    prisonApi.stubAgencies("INST", objectMapper.writeValueAsString(agencies))
    return this
  }

  fun withNomisCalculationReasons(reasons: List<NomisCalculationReason>): MockPrisonService {
    prisonApi.stubNomisCalculationReason(objectMapper.writeValueAsString(reasons))
    return this
  }

  fun withStub(mappingBuilder: MappingBuilder): StubMapping {
    return prisonApi.stubFor(mappingBuilder)
  }
}

class PrisonApiMockServer : WireMockServer(WIREMOCK_PORT) {
  companion object {
    private const val WIREMOCK_PORT = 8332
  }

  fun stubGetPrisonerDetails(prisonerId: String, json: String): StubMapping =
    stubFor(
      get("/api/offenders/$prisonerId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubGetSentencesAndOffences(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubGetReturnToCustody(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/bookings/$bookingId/fixed-term-recall")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubGetReturnToCustodyNotFound(bookingId: Long): StubMapping =
    stubFor(
      get("/api/bookings/$bookingId/fixed-term-recall")
        .willReturn(
          aResponse()
            .withStatus(404)
            .withHeader("Content-Type", "application/json")
            .withBody("""{"error": "Fixed Term Recall details not found"}"""),
        ),
    )

  fun stubGetSentenceAdjustments(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/adjustments/$bookingId/sentence-and-booking")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubPostOffenderDates(bookingId: Long): StubMapping =
    stubFor(
      post("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200),
        ),
    )

  fun stubOffenderFinePayments(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/offender-fine-payment/booking/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubExternalMovements(prisonerId: String, json: String): StubMapping =
    stubFor(
      get(urlPathEqualTo("/api/movements/offender/$prisonerId"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubPrisonCalculablePrisoners(establishmentId: String, json: String): StubMapping =
    stubFor(
      get("/api/prison/$establishmentId/booking/latest/paged/calculable-prisoner?page=0")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200),
        ),
    )

  fun stubCaseloads(json: String): StubMapping = stubFor(
    get(urlPathEqualTo("/api/users/me/caseLoads"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )

  fun stubAgencies(agencyType: String, json: String): StubMapping = stubFor(
    get(urlPathEqualTo("/api/agencies/type/$agencyType"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )

  fun stubNomisCalculationReason(json: String): StubMapping = stubFor(
    get(urlPathEqualTo("/api/reference-domains/domains/CALC_REASON/codes"))
      .willReturn(
        aResponse()
          .withHeader("Content-Type", "application/json")
          .withBody(json)
          .withStatus(200),
      ),
  )
}

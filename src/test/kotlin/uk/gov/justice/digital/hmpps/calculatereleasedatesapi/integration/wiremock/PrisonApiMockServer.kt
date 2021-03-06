package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.junit.jupiter.api.extension.AfterAllCallback
import org.junit.jupiter.api.extension.BeforeAllCallback
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation

/*
    This class mocks the prison-api.
    JSON files exist in 'src/test/resources/test_data/api_integration
    Add files to any sub-directory to automatically stub rest calls. The file name will act as the prisoner id.
    The hashcode of the prisoner id string, will be the booking id.
    Once a file is added to any of the directories, all calls will be stubbed, if not all directories have an
    entry for the given prisoner id, the mock will fallback to a default json file.
 */
class PrisonApiExtension : BeforeAllCallback, AfterAllCallback, BeforeEachCallback {
  companion object {
    @JvmField
    val prisonApi = PrisonApiMockServer()
    const val DEFAULT = "default"
    val log: Logger = LoggerFactory.getLogger(this::class.java)
  }
  private val jsonTransformation = JsonTransformation()
  private val objectMapper = TestUtil.objectMapper()

  override fun beforeAll(context: ExtensionContext) {
    prisonApi.start()

    val prisoners = jsonTransformation.getAllPrisonerDetails()
    val defaultPrisoner = prisoners[DEFAULT]!!

    val adjustments = jsonTransformation.getAllAdjustmentsJson()
    val defaultAdjustment = adjustments[DEFAULT]!!

    val sentences = jsonTransformation.getAllSentenceAndOffencesJson()
    val defaultSentence = sentences[DEFAULT]!!

    val returnToCustodyDates = jsonTransformation.getAllReturnToCustodyDatesJson()

    val allPrisoners = (adjustments.keys + sentences.keys + prisoners.keys).distinct()
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
        log.info("Stubbing adjustments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $it")
        adjustments[it]!!
      } else {
        log.info("Stubbing adjustments prisonerId $it, bookingId ${it.hashCode().toLong()} from file $DEFAULT")
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
      }

      prisonApi.stubPostOffenderDates(it.hashCode().toLong())
    }
  }

  override fun beforeEach(context: ExtensionContext) {
    prisonApi.resetRequests()
  }

  override fun afterAll(context: ExtensionContext) {
    prisonApi.stop()
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
            .withStatus(200)
        )
    )

  fun stubGetSentencesAndOffences(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/offender-sentences/booking/$bookingId/sentences-and-offences")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200)
        )
    )

  fun stubGetReturnToCustody(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/bookings/$bookingId/return-to-custody")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200)
        )
    )

  fun stubGetSentenceAdjustments(bookingId: Long, json: String): StubMapping =
    stubFor(
      get("/api/adjustments/$bookingId/sentence-and-booking")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(json)
            .withStatus(200)
        )
    )

  fun stubPostOffenderDates(bookingId: Long): StubMapping =
    stubFor(
      post("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withStatus(200)
        )
    )
}

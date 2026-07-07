package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.expectBody
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.storeOperativeSentenceEnvelope=true"])
class OperativeSentenceEnvelopeIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {

  @Test
  fun `should be able to get operative sentence enveloper when the latest calculation is from NOMIS`() {
    stubPrisoner(PRISONER_ID, prisonerDetails)
    stubKeyDates(
      BOOKING_ID,
      OffenderKeyDates(
        "NEW",
        now,
        "From NOMIS",
        conditionalReleaseDate = LocalDate.of(2030, 1, 6),
        sentenceExpiryDate = LocalDate.of(2025, 2, 14),
        conditionalReleaseDateOverridden = true,
        calculatedByUserId = "user1",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )
    stubSentencesAndOffencesFromPrisonApi()
    val operativeSentenceEnvelope = webTestClient.get()
      .uri("/operative-sentence-envelope/$PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__SENTENCE_ENVELOPE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody<OperativeSentenceEnvelope>()
      .returnResult().responseBody!!

    assertThat(operativeSentenceEnvelope).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 14,
        earliestSentenceStartDate = LocalDate.of(2025, 2, 1),
        isPostRecallSentenceEnvelope = null,
        containsAnSDSPlusSentence = null,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.NOMIS,
        bookingId = BOOKING_ID,
      ),
    )
  }

  @Test
  fun `should be able to get operative sentence enveloper when the latest calculation is a supported CRDS calculation`() {
    val bookingId = 1544803905L
    val prisonerId = "default"
    stubPrisoner(PRISONER_ID, prisonerDetails.copy(bookingId = bookingId))
    val prelim = createPreliminaryCalculation(prisonerId)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)

    val offenderKeyDates = OffenderKeyDates(
      "NEW",
      now,
      "From CRDS: ${confirmed.calculationReference}",
      calculatedByUserId = "user1",
      calculatedByFirstName = "User",
      calculatedByLastName = "One",
    )
    stubKeyDates(bookingId, offenderKeyDates)
    val operativeSentenceEnvelope = webTestClient.get()
      .uri("/operative-sentence-envelope/$prisonerId")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("CALCULATE_RELEASE_DATES__SENTENCE_ENVELOPE__RO")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody<OperativeSentenceEnvelope>()
      .returnResult().responseBody!!

    assertThat(operativeSentenceEnvelope).isEqualTo(
      OperativeSentenceEnvelope(
        sentenceEnvelopeLengthInDays = 601,
        earliestSentenceStartDate = LocalDate.of(2015, 3, 17),
        isPostRecallSentenceEnvelope = false,
        containsAnSDSPlusSentence = false,
        sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
        bookingId = bookingId,
      ),
    )
  }

  private fun stubSentencesAndOffencesFromPrisonApi() {
    mockPrisonService.withStub(
      (
        get("/api/offender-sentences/booking/$BOOKING_ID/sentences-and-offences")
          .willReturn(
            aResponse()
              .withHeader("Content-Type", "application/json")
              .withBody(TestUtil.objectMapper().writeValueAsString(listOf(sentenceAndOffence)))
              .withStatus(200),
          )
        ),
    )
  }

  private fun stubKeyDates(bookingId: Long, offenderKeyDates: OffenderKeyDates) {
    mockPrisonService.withStub(
      get("/api/offender-dates/$bookingId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(offenderKeyDates))
            .withStatus(200),
        ),
    )
  }

  private fun stubPrisoner(prisonerId: String, prisonerDetails: PrisonerDetails) {
    mockPrisonService.withStub(
      get("/api/offenders/$prisonerId")
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(objectMapper.writeValueAsString(prisonerDetails))
            .withStatus(200),
        ),
    )
  }

  companion object {
    private val now = LocalDateTime.now()
    private const val BOOKING_ID = 123456L
    private const val PRISONER_ID = "ABC123"
    private val prisonerDetails = PrisonerDetails(BOOKING_ID, PRISONER_ID, "Joe", "Bloggs", LocalDate.of(1970, 1, 1))
    private val sentenceAndOffence = PrisonApiSentenceAndOffences(
      bookingId = 123,
      sentenceDate = LocalDate.of(2025, 2, 1),
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceStatus = "A",
      sentenceCategory = "A",
      sentenceCalculationType = "ADIMP",
      sentenceTypeDescription = "Some SDS",
    )
  }
}

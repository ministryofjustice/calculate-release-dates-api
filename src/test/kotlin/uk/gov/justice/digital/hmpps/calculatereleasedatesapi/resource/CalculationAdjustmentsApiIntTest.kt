package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.useAdjustmentsApi=true"])
class CalculationAdjustmentsApiIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Adjustments API integration`() {
    val prisoner = "ADJ-API"

    // Firstly all adjustments are "NEW"
    var analysedAdjustments = getAnalysedAdjustments(prisoner)
    assertThat(analysedAdjustments.all { it.analysisResult == AdjustmentAnalysisResult.NEW }).isEqualTo(true)

    // Calculate release dates and confirm
    var result = createPreliminaryCalculation(prisoner)
    result = createConfirmCalculationForPrisoner(result.calculationRequestId)

    assertThat(result).isNotNull
    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2024, 12, 1))

    // Get stored adjustments in prison-api structure
    val storedPrisonApiStructuredAdjustments = getStoredAdjustmentsPrisonApiStructure(result.calculationRequestId)

    assertThat(storedPrisonApiStructuredAdjustments.sentenceAdjustments.size).isEqualTo(1)
    assertThat(storedPrisonApiStructuredAdjustments.sentenceAdjustments[0]).isEqualTo(
      SentenceAdjustment(
        type = SentenceAdjustmentType.REMAND,
        fromDate = LocalDate.of(2023, 2, 1),
        toDate = LocalDate.of(2023, 4, 1),
        active = true,
        numberOfDays = 60,
        sentenceSequence = 1,
      ),
    )
    assertThat(storedPrisonApiStructuredAdjustments.bookingAdjustments.size).isEqualTo(2)
    assertThat(storedPrisonApiStructuredAdjustments.bookingAdjustments[0]).isEqualTo(
      BookingAdjustment(
        type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
        fromDate = LocalDate.of(2017, 2, 3),
        toDate = null,
        active = true,
        numberOfDays = 23,
      ),
    )
    assertThat(storedPrisonApiStructuredAdjustments.bookingAdjustments[1]).isEqualTo(
      BookingAdjustment(
        type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
        fromDate = LocalDate.of(2018, 10, 28),
        toDate = LocalDate.of(2018, 11, 3),
        active = true,
        numberOfDays = 7,
      ),
    )

    // Get stored adjustments in adjustments-api structure
    val storedAdjustmentsApiAdjustments = getStoredAdjustmentsApiStructure(result.calculationRequestId)

    val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

    assertThat(calculationRequest.adjustmentsVersion).isEqualTo(1)
    assertThat(calculationRequest.adjustments.toString()).isEqualTo(TestUtil.objectMapper().writeValueAsString(storedAdjustmentsApiAdjustments))

    // Check Analysed adjustments are now all SAME.
    analysedAdjustments = getAnalysedAdjustments(prisoner)

    assertThat(analysedAdjustments.all { it.analysisResult == AdjustmentAnalysisResult.SAME }).isEqualTo(true)
  }

  private fun getStoredAdjustmentsApiStructure(calculationRequestId: Long) = webTestClient.get()
    .uri("/calculation/adjustments/$calculationRequestId?adjustments-api=true")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(object : ParameterizedTypeReference<List<AdjustmentDto>>() {})
    .returnResult().responseBody!!

  private fun getStoredAdjustmentsPrisonApiStructure(calculationRequestId: Long) = webTestClient.get()
    .uri("/calculation/adjustments/$calculationRequestId")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(BookingAndSentenceAdjustments::class.java)
    .returnResult().responseBody!!
  private fun getAnalysedAdjustments(prisoner: String) = webTestClient.get()
    .uri("/adjustments/$prisoner")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(object : ParameterizedTypeReference<List<AnalysedAdjustment>>() {})
    .returnResult().responseBody!!
}

package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.BookingCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import javax.persistence.EntityNotFoundException

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class CalculationIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Run calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val result = createPreliminaryCalculation()

    if (result != null) {
      val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

      assertThat(result).isNotNull
      assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
      assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
      assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
      assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
      assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
        .isEqualTo("2015-03-17")
    }
  }

  @Test
  fun `Confirm a calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val result = createConfirmCalculationForPrisoner(createPreliminaryCalculation().calculationRequestId)

    val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

    assertThat(result).isNotNull
    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    assertThat(calculationRequest.calculationStatus).isEqualTo("CONFIRMED")
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText()).isEqualTo("2015-03-17")
  }

  @Test
  fun `Get the results for a confirmed calculation`() {
    createConfirmCalculationForPrisoner(createPreliminaryCalculation().calculationRequestId)

    val result = webTestClient.get()
      .uri("/calculation/results/$PRISONER_ID/$BOOKING_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingCalculation::class.java)
      .returnResult().responseBody

    assertThat(result).isNotNull

    if (result != null && result.dates.containsKey(SLED)) {
      assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    }
    if (result != null && result.dates.containsKey(CRD)) {
      assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    }
    if (result != null && result.dates.containsKey(TUSED)) {
      assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    }
  }

  private fun createPreliminaryCalculation() = webTestClient.post()
    .uri("/calculation/$PRISONER_ID")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(BookingCalculation::class.java)
    .returnResult().responseBody

  private fun createConfirmCalculationForPrisoner(calculationRequestId: Long): BookingCalculation {
    return webTestClient.post()
      .uri("/calculation/$PRISONER_ID/confirm/$calculationRequestId")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingCalculation::class.java)
      .returnResult().responseBody
  }

  @Test
  fun `Attempt to get the results for a confirmed calculation where no confirmed calculation exists`() {
    webTestClient.get()
      .uri("/calculation/results/$PRISONER_ID/$BOOKING_ID_DOESNT_EXIST")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().is4xxClientError
      .expectBody()
      .json(
        "{\"status\":404,\"errorCode\":null,\"userMessage\":\"Not found: No confirmed calculation exists " +
          "for prisoner A1234AA and bookingId 92929988\",\"developerMessage\":\"No confirmed calculation exists for " +
          "prisoner A1234AA and bookingId 92929988\"," +
          "\"moreInfo\":null}"
      )
  }

  @Test
  fun `Get the calculation breakdown for a calculation`() {
    val calc = createConfirmCalculationForPrisoner(createPreliminaryCalculation().calculationRequestId)

    val result = webTestClient.get()
      .uri("/calculation/breakdown/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationBreakdown::class.java)
      .returnResult().responseBody

    assertThat(result).isNotNull
    assertThat(result.consecutiveSentence).isNull()
    assertThat(result.concurrentSentences).hasSize(1)
    assertThat(result.concurrentSentences.get(0).dates[SLED]!!.unadjusted).isEqualTo(LocalDate.of(2016, 11, 16))
    assertThat(result.concurrentSentences.get(0).dates[SLED]!!.adjusted).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.concurrentSentences.get(0).dates[SLED]!!.daysBetween).isEqualTo(10)
  }

  companion object {
    const val BOOKING_ID = 9292L
    const val BOOKING_ID_DOESNT_EXIST = 92929988L
    const val PRISONER_ID = "A1234AA"
  }
}

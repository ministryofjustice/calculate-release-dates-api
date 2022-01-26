package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
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
    val result = createPreliminaryCalculation(PRISONER_ID)

    if (result != null) {
      val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

      assertThat(result).isNotNull
      assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
      assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
      assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
      assertThat(result.dates[HDCED]).isEqualTo(LocalDate.of(2015, 8, 25))
      assertThat(result.dates[ESED]).isEqualTo(LocalDate.of(2016, 11, 16))
      assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
      assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
        .isEqualTo("2015-03-17")
    }
  }

  @Test
  fun `Confirm a calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {

    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    var result: BookingCalculation? = null
    var calculationRequest: CalculationRequest? = null
    if (resultCalculation != null) {
      result = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)
      calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }
    }

    assertThat(result).isNotNull
    if (result != null) {
      assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
      assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
      assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    }

    assertThat(calculationRequest).isNotNull
    if (calculationRequest != null) {
      assertThat(calculationRequest.calculationStatus).isEqualTo("CONFIRMED")
      assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
      assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText()).isEqualTo("2015-03-17")
    }
  }

  @Test
  fun `Get the results for a confirmed calculation`() {

    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    if (resultCalculation != null) {
      createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)
    }

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

  @Test
  fun `Get the results for a calc that causes an error `() {
    val result = webTestClient.post()
      .uri("/calculation/$PRISONER_ERROR_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().is5xxServerError
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    assertThat(result).isNotNull

    val req = calculationRequestRepository
      .findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtAsc(
        PRISONER_ERROR_ID, BOOKING_ERROR_ID, CalculationStatus.ERROR.name
      )

    assertThat(req).isNotNull
  }

  private fun createPreliminaryCalculation(prisonerid: String) = webTestClient.post()
    .uri("/calculation/$prisonerid")
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
          "for prisoner default and bookingId 92929988\",\"developerMessage\":\"No confirmed calculation exists for " +
          "prisoner default and bookingId 92929988\"," +
          "\"moreInfo\":null}"
      )
  }

  @Test
  fun `Get the calculation breakdown for a calculation`() {

    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    var calc: BookingCalculation? = null
    if (resultCalculation != null) {
      calc = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)
    }

    assertThat(calc).isNotNull

    if (calc != null) {
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
      if (result != null) {
        assertThat(result.consecutiveSentence).isNull()
        assertThat(result.concurrentSentences).hasSize(1)
        assertThat(result.concurrentSentences[0].dates[SLED]!!.unadjusted).isEqualTo(LocalDate.of(2016, 11, 16))
        assertThat(result.concurrentSentences[0].dates[SLED]!!.adjusted).isEqualTo(LocalDate.of(2016, 11, 6))
        assertThat(result.concurrentSentences[0].dates[SLED]!!.adjustedByDays).isEqualTo(10)
      }
    }
  }

  @Test
  fun `Run calculation for a sex offender (check HDCED not set - based on example 13 from the unit tests)`() {
    val result = createPreliminaryCalculation(PRISONER_ID_SEX_OFFENDER)

    if (result != null) {
      val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

      assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
      assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
      assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
      assertThat(result.dates[ESED]).isEqualTo(LocalDate.of(2016, 11, 16))
      assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID_SEX_OFFENDER)
      assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
        .isEqualTo("2015-03-17")
      assert(!result.dates.containsKey(HDCED))
    }
  }

  @Test
  fun `Run calculation where remand periods overlap with other remand periods`() {
    val error: ErrorResponse = webTestClient.post()
      .uri("/calculation/$REMAND_OVERLAPS_WITH_REMAND_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    assertThat(error.userMessage).isEqualTo("Remand of range 2000-04-01/2000-04-30 overlaps with remand of range 2000-04-28/2000-04-30")
    assertThat(error.errorCode).isEqualTo("REMAND_OVERLAPS_WITH_REMAND")
  }

  @Test
  fun `Run calculation where remand periods overlap with a sentence periods`() {
    val error: ErrorResponse = webTestClient.post()
      .uri("/calculation/$REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    assertThat(error.userMessage).isEqualTo("Remand of range 2000-04-28/2000-04-30 overlaps with sentence of range 2000-04-29/2001-02-25")
    assertThat(error.errorCode).isEqualTo("REMAND_OVERLAPS_WITH_SENTENCE")
  }

  companion object {
    const val PRISONER_ID = "default"
    const val PRISONER_ERROR_ID = "123CBA"
    const val PRISONER_ID_SEX_OFFENDER = "S3333XX"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
    val BOOKING_ERROR_ID = PRISONER_ERROR_ID.hashCode().toLong()
    const val BOOKING_ID_DOESNT_EXIST = 92929988L
    const val REMAND_OVERLAPS_WITH_REMAND_PRISONER_ID = "REM-REM"
    const val REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID = "REM-SEN"
  }
}

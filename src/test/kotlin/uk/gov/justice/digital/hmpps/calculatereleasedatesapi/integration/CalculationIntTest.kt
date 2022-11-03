package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_18M_LT_4Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.A_FINE_SENTENCE_WITH_PAYMENTS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.MULTIPLE_SENTENCES_CONSECUTIVE_TO
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_DATE_AFTER_SENTENCE_START_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.OFFENCE_MISSING_DATE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.PRISONER_SUBJECT_TO_PTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_FROM_TO_DATES_REQUIRED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_REMAND
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SEC_91_SENTENCE_TYPE_INCORRECT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.SENTENCE_HAS_MULTIPLE_TERMS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.UNSUPPORTED_SENTENCE_TYPE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.ZERO_IMPRISONMENT_TERM
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate
import javax.persistence.EntityNotFoundException

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
class CalculationIntTest : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Autowired
  lateinit var objectMapper: ObjectMapper

  @Test
  fun `Run calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val result = createPreliminaryCalculation(PRISONER_ID)

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

  @Test
  fun `Confirm a calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {

    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId, PRISONER_ID)
    val calculationRequest: CalculationRequest = calculationRequestRepository.findById(confirmed.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${confirmed.calculationRequestId}") }

    assertThat(confirmed.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(confirmed.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(confirmed.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))

    assertThat(calculationRequest.calculationStatus).isEqualTo("CONFIRMED")
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText()).isEqualTo("2015-03-17")
  }

  @Test
  fun `Get the results for a confirmed calculation`() {

    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId, PRISONER_ID)

    val result = webTestClient.get()
      .uri("/calculation/results/$PRISONER_ID/$BOOKING_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
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
      .findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
        PRISONER_ERROR_ID, BOOKING_ERROR_ID, CalculationStatus.ERROR.name
      )

    assertThat(req).isNotNull
  }

  private fun createPreliminaryCalculation(prisonerid: String): CalculatedReleaseDates = webTestClient.post()
    .uri("/calculation/$prisonerid")
    .accept(MediaType.APPLICATION_JSON)
    .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
    .exchange()
    .expectStatus().isOk
    .expectHeader().contentType(MediaType.APPLICATION_JSON)
    .expectBody(CalculatedReleaseDates::class.java)
    .returnResult().responseBody

  private fun createConfirmCalculationForPrisoner(
    calculationRequestId: Long,
    prisonerId: String
  ): CalculatedReleaseDates {
    return webTestClient.post()
      .uri("/calculation/$prisonerId/confirm/$calculationRequestId")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(CalculationFragments("<p>BREAKDOWN</p>")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!
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
    val calc = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId, PRISONER_ID)

    val result = webTestClient.get()
      .uri("/calculation/breakdown/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationBreakdown::class.java)
      .returnResult().responseBody!!

    assertThat(result).isNotNull
    assertThat(result.consecutiveSentence).isNull()
    assertThat(result.concurrentSentences).hasSize(1)
    assertThat(result.concurrentSentences[0].dates[SLED]!!.unadjusted).isEqualTo(LocalDate.of(2016, 11, 16))
    assertThat(result.concurrentSentences[0].dates[SLED]!!.adjusted).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.concurrentSentences[0].dates[SLED]!!.adjustedByDays).isEqualTo(10)
    assertThat(result.breakdownByReleaseDateType.keys).isEqualTo(setOf(CRD, SLED, TUSED, HDCED))
    assertThat(result.breakdownByReleaseDateType[TUSED]!!.rules).isEqualTo(setOf(TUSED_LICENCE_PERIOD_LT_1Y))
    assertThat(result.breakdownByReleaseDateType[HDCED]!!.rules).isEqualTo(setOf(HDCED_GE_18M_LT_4Y))
  }

  @Test
  fun `Run calculation for a sex offender (check HDCED not set - based on example 13 from the unit tests)`() {
    val result = createPreliminaryCalculation(PRISONER_ID_SEX_OFFENDER)

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

    assertThat(error.userMessage).isEqualTo(REMAND_OVERLAPS_WITH_SENTENCE.message)
    assertThat(error.errorCode).isEqualTo(REMAND_OVERLAPS_WITH_SENTENCE.name)
  }

  @Test
  fun `Run validation where remand periods overlap with a sentence periods`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/$REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(REMAND_OVERLAPS_WITH_SENTENCE)
      )
    )
  }

  @Test
  fun `Run validation on future dated adjustments`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/CRS-1044/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(ADJUSTMENT_FUTURE_DATED_RADA)
      )
    )
  }

  @Test
  fun `Run calculation where SDS+ is consecutive to SDS`() {
    val calculatedReleaseDates: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/$SDS_PLUS_CONSECUTIVE_TO_SDS")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculatedReleaseDates.dates[SLED]).isEqualTo(LocalDate.of(2030, 11, 30))
    assertThat(calculatedReleaseDates.dates[CRD]).isEqualTo(LocalDate.of(2027, 7, 1))
  }

  @Test
  fun `Run validation on invalid data`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/$VALIDATION_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(code = OFFENCE_DATE_AFTER_SENTENCE_START_DATE, arguments = listOf("1", "1")),
        ValidationMessage(code = OFFENCE_DATE_AFTER_SENTENCE_RANGE_DATE, arguments = listOf("1", "2")),
        ValidationMessage(code = OFFENCE_MISSING_DATE, arguments = listOf("2", "1")),
        ValidationMessage(code = ZERO_IMPRISONMENT_TERM, arguments = listOf("2", "1")),
        ValidationMessage(code = SENTENCE_HAS_MULTIPLE_TERMS, arguments = listOf("2", "2")),
        ValidationMessage(code = SEC_91_SENTENCE_TYPE_INCORRECT, arguments = listOf("2", "4")),
        ValidationMessage(code = SEC_91_SENTENCE_TYPE_INCORRECT, arguments = listOf("2", "4")),
        ValidationMessage(code = MULTIPLE_SENTENCES_CONSECUTIVE_TO, arguments = listOf("2", "2")),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = REMAND_FROM_TO_DATES_REQUIRED),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_ADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_RADA),
        ValidationMessage(code = ADJUSTMENT_FUTURE_DATED_UAL),
        ValidationMessage(code = REMAND_OVERLAPS_WITH_REMAND)
      )
    )
  }

  @Test
  fun `Run validation on unsupported sentence data`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/$UNSUPPORTED_SENTENCE_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(UNSUPPORTED_SENTENCE_TYPE, listOf("2003", "This sentence is unsupported"))
      )
    )
  }

  @Test
  fun `Run validation on valid data`() {
    val res = webTestClient.post()
      .uri("/calculation/$PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(res).isEmpty()
  }

  @Test
  fun `Run calculation on invalid data`() {
    val errorResponse: ErrorResponse = webTestClient.post()
      .uri("/calculation/$VALIDATION_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    assertThat(errorResponse.userMessage).isEqualTo(
      ERROR_MESSAGE_STRING.trimIndent()
    )
  }

  @Test
  fun `Run calculation on unsupported data`() {
    val errorResponse: ErrorResponse = webTestClient.post()
      .uri("/calculation/$UNSUPPORTED_SENTENCE_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY)
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody!!

    assertThat(errorResponse.userMessage).isEqualTo(
      """The validation has failed with errors:
    Unsupported sentence type 2003 This sentence is unsupported
      """.trimIndent()
    )
  }

  @Test
  fun `Run calculation on inactive data`() {
    val calculatedReleaseDates: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/$INACTIVE_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculatedReleaseDates.dates[CRD]).isEqualTo("2021-07-21")

    val result = webTestClient.get()
      .uri("/calculation/breakdown/${calculatedReleaseDates.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationBreakdown::class.java)
      .returnResult().responseBody!!

    // Inactive sentences have been filtered
    assertThat(result.concurrentSentences).hasSize(1)
  }

  @Test
  fun `Get the source prison api data and html for a calculation`() {
    val resultCalculation = createPreliminaryCalculation("14FTR")
    val calc = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId, "14FTR")

    val results = webTestClient.get()
      .uri("/calculation/results/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(results.calculationFragments?.breakdownHtml).isEqualTo("<p>BREAKDOWN</p>")

    val sentenceAndOffences = webTestClient.get()
      .uri("/calculation/sentence-and-offences/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(object : ParameterizedTypeReference<List<SentenceAndOffences>>() {})
      .returnResult().responseBody!!

    assertThat(sentenceAndOffences).isNotNull

    val prisonerDetails = webTestClient.get()
      .uri("/calculation/prisoner-details/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(PrisonerDetails::class.java)
      .returnResult().responseBody!!

    assertThat(prisonerDetails).isNotNull

    val adjustments = webTestClient.get()
      .uri("/calculation/adjustments/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingAndSentenceAdjustments::class.java)
      .returnResult().responseBody!!

    assertThat(adjustments).isNotNull

    val returnToCustody = webTestClient.get()
      .uri("/calculation/return-to-custody/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ReturnToCustodyDate::class.java)
      .returnResult().responseBody!!

    assertThat(returnToCustody).isNotNull
  }

  @Test
  fun `Run calculation on recall`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/RECALL")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 7, 3)
    )
  }

  @Test
  fun `Run calculation on 14 day fixed term recall`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/14FTR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 3, 14)
    )
  }

  @Test
  fun `Run calculation on 28 day fixed term recall`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/28FTR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 3, 28)
    )
  }

  @Test
  fun `Run validation on extinguished crd booking`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/EXTINGUISH/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND)
      )
    )
  }

  @Test
  fun `Run calculation on pre prod bug where adjustments are applied to wrong sentences CRS-829 AC-1`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-829-1")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2025, 8, 28)
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2029, 3, 28)
    )
  }

  @Test
  fun `Run calculation on pre prod bug where adjustments are applied to wrong sentences CRS-829 AC-2`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-829-2")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2025, 8, 30)
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2029, 3, 30)
    )
  }

  @Test
  fun `Run validation on argument after release date 1`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/CRS-796-1/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)
      )
    )
  }

  @Test
  fun `Run validation on argument after release date 2`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/CRS-796-2/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(ADJUSTMENT_AFTER_RELEASE_ADA)
      )
    )
  }

  @Test
  fun `Run calculation on CRS-872 a consecutive sentence having multiple offences, some schedule 15 attracting life, some not`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-872")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2027, 6, 20)
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2030, 2, 4)
    )
  }

  @Test
  fun `Run calculation on EDS sentence`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/EDS")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2020, 4, 14)
    )
    assertThat(calculation.dates[PED]).isEqualTo(
      LocalDate.of(2019, 5, 21)
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2023, 7, 20)
    )
  }

  @Test
  fun `Run calculation on SOPC sentence`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/SOPC")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2032, 6, 16)
    )
    assertThat(calculation.dates[PED]).isEqualTo(
      LocalDate.of(2030, 8, 7)
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2033, 6, 16)
    )
  }

  @Test
  fun `Run calculation on adjustment linked to inactive sentence CRS-892`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-892")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2025, 4, 19)
    )
  }

  @Test
  fun `Run calculation on a PCSC section 250`() {
    val userInput = CalculationUserInputs(
      listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03013A",
          userInputType = UserInputType.SECTION_250,
          userChoice = true
        )
      )
    )
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/SEC250")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(userInput))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2027, 2, 26)
    )
  }

  @Test
  fun `Run validation on unsupported prisoner data`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/$UNSUPPORTED_PRISONER_PRISONER_ID/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(PRISONER_SUBJECT_TO_PTD)
      )
    )
  }

  @Test
  fun `Run calculation on a test of historic inactive released prisoner`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/OUT_CALC/test")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2021, 12, 29)
    )
  }

  @Test
  fun `Run calculation on a test of prisoner`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/default/test")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
  }

  @Test
  fun `Run calculation on A FINE sentence`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/AFINE")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[ARD]).isEqualTo(
      LocalDate.of(2029, 6, 4)
    )
    assertThat(calculation.dates[SED]).isEqualTo(
      LocalDate.of(2029, 6, 4)
    )
  }

  @Test
  fun `Run validation on on prisoner with fine payment`() {
    val validationMessages = webTestClient.post()
      .uri("/calculation/PAYMENTS/validate")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBodyList(ValidationMessage::class.java)
      .returnResult().responseBody!!

    assertThat(validationMessages).isEqualTo(
      listOf(
        ValidationMessage(A_FINE_SENTENCE_WITH_PAYMENTS)
      )
    )
  }

  companion object {
    const val PRISONER_ID = "default"
    const val PRISONER_ERROR_ID = "123CBA"
    const val PRISONER_ID_SEX_OFFENDER = "S3333XX"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
    val BOOKING_ERROR_ID = PRISONER_ERROR_ID.hashCode().toLong()
    const val BOOKING_ID_DOESNT_EXIST = 92929988L
    const val REMAND_OVERLAPS_WITH_SENTENCE_PRISONER_ID = "REM-SEN"
    const val SDS_PLUS_CONSECUTIVE_TO_SDS = "SDS-CON"
    const val VALIDATION_PRISONER_ID = "VALIDATION"
    const val UNSUPPORTED_SENTENCE_PRISONER_ID = "UNSUPP_SENT"
    const val UNSUPPORTED_PRISONER_PRISONER_ID = "UNSUPP_PRIS"
    const val INACTIVE_PRISONER_ID = "INACTIVE"
    private const val ERROR_MESSAGE_STRING = """The validation has failed with errors:
    The offence date for court case 1 count 1 must be before the sentence date.
    The offence date range for court case 1 count 2 must be before the sentence date.
    The calculation must include an offence date for court case 2 count 1.
    Court case 2 count 1 must include an imprisonment term greater than zero.
    Court case 2 count 2 must only have one term in NOMIS.
    The sentence type for court case 2 count 4 is invalid for the sentence date entered.
    The sentence type for court case 2 count 4 is invalid for the sentence date entered.
    There are multiple sentences that are consecutive to court case 2 count 2. A sentence should only have one other sentence consecutive to it.
    Remand periods must have a from and to date.
    Remand periods must have a from and to date.
    The from date for Additional days awarded (ADA) should be the date of the adjudication hearing.
    The from date for Restored additional days awarded (RADA) must be the date the additional days were remitted.
    The from date for Unlawfully at Large (UAL) must be the first day the prisoner was deemed UAL.
    Remand time can only be added once, it can cannot overlap with other remand dates.
"""
  }
}

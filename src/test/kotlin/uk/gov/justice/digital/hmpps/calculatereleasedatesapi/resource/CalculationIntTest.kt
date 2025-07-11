package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlMatching
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.ErrorResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDCED_GE_MIN_PERIOD_LT_MIDPOINT
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.HDC_180
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule.TUSED_LICENCE_PERIOD_LT_1Y
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.APD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ARD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ERSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ETD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.MTD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.PRRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockManageOffencesClient
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationSentenceUserInput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemand
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandCalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RelevantRemandSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.UserInputType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.OffencePcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.manageoffencesapi.PcscMarkers
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.Duration
import java.time.LocalDate

class CalculationIntTest(private val mockManageOffencesClient: MockManageOffencesClient) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  @Test
  fun `Run calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val result = createPreliminaryCalculation(PRISONER_ID)

    val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

    assertThat(result).isNotNull
    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    assertThat(result.dates[HDCED]).isEqualTo(LocalDate.of(2015, 8, 7))
    assertThat(result.dates[ESED]).isEqualTo(LocalDate.of(2016, 11, 16))
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
      .isEqualTo("2015-03-17")
  }

  @Test
  fun `Confirm a calculation for a prisoner (based on example 13 from the unit tests) + test input JSON in DB`() {
    val prelim = createPreliminaryCalculation(PRISONER_ID)
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)
    val calculationRequest: CalculationRequest =
      calculationRequestRepository.findById(confirmed.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${confirmed.calculationRequestId}") }

    assertThat(confirmed.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(confirmed.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(confirmed.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))

    assertThat(calculationRequest.calculationStatus).isEqualTo("CONFIRMED")
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(PRISONER_ID)
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText()).isEqualTo("2015-03-17")
  }

  @Test
  fun `Confirm calculation for a prisoner where CONCURRENT_CONSECUTIVE validation is ignored`() {
    mockManageOffencesClient.noneInPCSC(listOf("SX03001,TH68007A,TR68132"))
    val prelim = createPreliminaryCalculation("CRS-2370")
    val confirmed = createConfirmCalculationForPrisoner(prelim.calculationRequestId)
    val calculationRequest: CalculationRequest =
      calculationRequestRepository.findById(confirmed.calculationRequestId)
        .orElseThrow { EntityNotFoundException("No calculation request exists for id ${confirmed.calculationRequestId}") }

    assertThat(calculationRequest.calculationStatus).isEqualTo("CONFIRMED")
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo("CRS-2370")
  }

  @Test
  fun `Get the results for a confirmed calculation`() {
    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)

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
  fun `Get the results for a confirmed calculation by reference`() {
    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)

    val result = webTestClient.get()
      .uri("/calculationReference/${resultCalculation.calculationReference}")
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
  fun `Get the results for a confirmed calculation by reference with check is true`() {
    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)

    val result = webTestClient.get()
      .uri("/calculationReference/${resultCalculation.calculationReference}?checkForChange=true")
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
      .accept(MediaType.APPLICATION_JSON).bodyValue(calculationRequestModel)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().is5xxServerError
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ErrorResponse::class.java)
      .returnResult().responseBody

    assertThat(result).isNotNull

    val req = calculationRequestRepository
      .findFirstByPrisonerIdAndBookingIdAndCalculationStatusOrderByCalculatedAtDesc(
        PRISONER_ERROR_ID,
        BOOKING_ERROR_ID,
        CalculationStatus.ERROR.name,
      )

    assertThat(req).isNotNull
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
          "\"moreInfo\":null}",
      )
  }

  @Test
  fun `Get the calculation breakdown for a calculation`() {
    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    val calc = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)

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
    assertThat(result.breakdownByReleaseDateType[HDCED]!!.rules).isEqualTo(setOf(HDCED_GE_MIN_PERIOD_LT_MIDPOINT, HDC_180))
  }

  @Test
  fun `Confirm a calculation with approved dates`() {
    val resultCalculation = createPreliminaryCalculation(PRISONER_ID)
    val calc = createConfirmCalculationForPrisoner(
      resultCalculation.calculationRequestId,
      listOf(ManualEntrySelectedDate(APD, "APD", SubmittedDate(3, 3, 2023))),
    )
    assertThat(calc).isNotNull
    val result = webTestClient.get()
      .uri("/calculation/results/$PRISONER_ID/$BOOKING_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody
    assertThat(result!!.approvedDates).isNotNull
    assertThat(result.approvedDates).containsExactly(entry(APD, LocalDate.of(2023, 3, 3)))
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
  fun `Run calculation for on sex offender register (check HDCED not set - based on example 13 from the unit tests)`() {
    val result = createPreliminaryCalculation(PRISONER_ID_ON_SEX_OFFENDER_REGISTER)

    val calculationRequest = calculationRequestRepository.findById(result.calculationRequestId)
      .orElseThrow { EntityNotFoundException("No calculation request exists for id ${result.calculationRequestId}") }

    assertThat(result.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
    assertThat(result.dates[CRD]).isEqualTo(LocalDate.of(2016, 1, 6))
    assertThat(result.dates[TUSED]).isEqualTo(LocalDate.of(2017, 1, 6))
    assertThat(result.dates[ESED]).isEqualTo(LocalDate.of(2016, 11, 16))
    assertThat(calculationRequest.inputData["offender"]["reference"].asText()).isEqualTo(
      PRISONER_ID_ON_SEX_OFFENDER_REGISTER,
    )
    assertThat(calculationRequest.inputData["sentences"][0]["offence"]["committedAt"].asText())
      .isEqualTo("2015-03-17")
    assert(!result.dates.containsKey(HDCED))
  }

  @Test
  fun `Run calculation on inactive data`() {
    val calculatedReleaseDates: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/$INACTIVE_PRISONER_ID")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
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
    val calc = createConfirmCalculationForPrisoner(resultCalculation.calculationRequestId)

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

    val sentenceAndOffences = getSentencesAndOffencesForCalculation(calc.calculationRequestId)

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

    val bookingAndSentenceAdjustments = webTestClient.get()
      .uri("/calculation/adjustments/${calc.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(BookingAndSentenceAdjustments::class.java)
      .returnResult().responseBody!!

    assertThat(bookingAndSentenceAdjustments).isNotNull

    val adjustments = webTestClient.get()
      .uri("/calculation/adjustments/${calc.calculationRequestId}?adjustments-api=true")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(object : ParameterizedTypeReference<List<AdjustmentDto>>() {})
      .returnResult().responseBody!!

    assertThat(adjustments).isNotNull

    assertThat(adjustments.size).isEqualTo(bookingAndSentenceAdjustments.bookingAdjustments.size + bookingAndSentenceAdjustments.sentenceAdjustments.size)

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
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 7, 3),
    )
  }

  @Test
  fun `Run calculation on 14 day fixed term recall`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/14FTR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 1, 31),
    )
  }

  @Test
  fun `Run calculation on 28 day fixed term recall`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/28FTR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2022, 3, 28),
    )
  }

  @Test
  fun `Run calculation on 14 day fixed term recall with no returntocustodydate`() {
    webTestClient.post()
      .uri("/calculation/BLANKFTR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR)
      .expectBody()
      .consumeWith { response ->
        val responseBody = response.responseBody?.let { String(it) } ?: ""
        assertThat(responseBody).contains("No valid Return To Custody Date found") // Basic check for relevant message
        assertThat(responseBody).contains("\"status\":500") // Check for field mention
      }
  }

  @Test
  fun `Run calculation on pre prod bug where adjustments are applied to wrong sentences CRS-829 AC-1`() {
    mockManageOffencesClient.noneInPCSC(listOf("FI68421", "MD71131C", "MD71230", "MD71239"))
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-829-1")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2015, 8, 28),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2019, 3, 28),
    )
  }

  @Test
  fun `Run calculation on pre prod bug where adjustments are applied to wrong sentences CRS-829 AC-2`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-829-2")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2015, 8, 30),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2019, 3, 30),
    )
  }

  @Test
  fun `Run calculation on CRS-872 a consecutive sentence having multiple offences, some schedule 15 attracting life, some not`() {
    val userInput = CalculationUserInputs(
      useOffenceIndicators = true,
    )
    mockManageOffencesClient.withStub(
      get(urlMatching("/schedule/sds-early-release-exclusions\\?offenceCodes=TH68007A,TR68132"))
        .willReturn(
          aResponse()
            .withHeader("Content-Type", "application/json")
            .withBody(
              """[
                {
                    "offenceCode": "TH68007A",
                    "schedulePart": "SEXUAL"
                },
                {
                    "offenceCode": "TR68132",
                    "schedulePart": "SEXUAL"
                }
            ]
              """.trimIndent(),
            )
            .withStatus(200)
            .withTransformers("response-template"),
        ),
    )

    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "TH68007A",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      OffencePcscMarkers(
        offenceCode = "TR68132",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      OffencePcscMarkers(
        offenceCode = "SX03001",
        pcscMarkers = PcscMarkers(inListA = true, inListB = false, inListC = false, inListD = false),
      ),
      offences = "SX03001,TH68007A,TR68132",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-872")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .contentType(MediaType.APPLICATION_JSON)
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2027, 6, 20),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2030, 2, 4),
    )
  }

  @Test
  fun `Run calculation CRS-2321 for SEC91_03_ORA sentence 7 years or over where Section 250 SDS rules apply, preventing HDCED`() {
    val userInput = CalculationUserInputs(
      useOffenceIndicators = true,
    )

    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "OF61016",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = true, inListD = false),
      ),
      offences = "OF61016",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-2321-1")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates.containsKey(HDCED)).isFalse()
  }

  @Test
  fun `Run calculation CRS-2321 for SEC91_03 sentence 7 years or over where Section 250 SDS rules apply, preventing HDCED`() {
    val userInput = CalculationUserInputs(
      useOffenceIndicators = true,
    )

    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "OF61016",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = true, inListD = false),
      ),
      offences = "OF61016",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-2321-2")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates.containsKey(HDCED)).isFalse()
  }

  @Test
  fun `Run calculation CRS-2321 for SEC91_03 sentence under 7 years producing HDCED`() {
    val userInput = CalculationUserInputs(
      useOffenceIndicators = true,
    )

    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "OF61016",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = true, inListD = false),
      ),
      offences = "OF61016",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-2321-3")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates.containsKey(HDCED)).isTrue()
  }

  @Test
  fun `Run calculation CRS-2321 for SEC91_03_ORA sentence under 7 years producing HDCED`() {
    val userInput = CalculationUserInputs(
      useOffenceIndicators = true,
    )

    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "OF61016",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = true, inListD = false),
      ),
      offences = "OF61016",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-2321-4")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates.containsKey(HDCED)).isTrue()
  }

  @Test
  fun `Run calculation on EDS sentence`() {
    mockManageOffencesClient.noneInPCSC(listOf("CD79009", "TR68132"))
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/EDS")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2020, 4, 14),
    )
    assertThat(calculation.dates[PED]).isEqualTo(
      LocalDate.of(2019, 5, 21),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2023, 7, 20),
    )
  }

  @Test
  fun `Run calculation on SOPC sentence`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/SOPC")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2032, 6, 16),
    )
    assertThat(calculation.dates[PED]).isEqualTo(
      LocalDate.of(2030, 8, 7),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2033, 6, 16),
    )
  }

  @Test
  fun `Run calculation on adjustment linked to inactive sentence CRS-892`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-892")
      .accept(MediaType.APPLICATION_JSON)
      .bodyValue(calculationRequestModel)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2025, 4, 19),
    )
  }

  @Test
  fun `Run calculation on a PCSC section 250`() {
    val userInput = CalculationUserInputs(
      calculateErsed = true,
      sentenceCalculationUserInputs = listOf(
        CalculationSentenceUserInput(
          sentenceSequence = 1,
          offenceCode = "SX03013A",
          userInputType = UserInputType.SECTION_250,
          userChoice = true,
        ),
      ),
    )
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/SEC250")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(CalculationRequestModel(userInput, 1L))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2027, 2, 26),
    )
    assertThat(calculation.dates[ERSED]).isEqualTo(
      LocalDate.of(2025, 8, 31),
    )
  }

  @Test
  fun `Run calculation on a test of historic inactive released prisoner`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "TH68010A",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      OffencePcscMarkers(
        offenceCode = "TH68164",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      offences = "TH68010A,TH68164",
    )

    val calculation: CalculationResults = webTestClient.post()
      .uri("/calculation/OUT_CALC/test")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationResults::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.calculatedReleaseDates!!.dates[CRD]).isEqualTo(
      LocalDate.of(2021, 12, 29),
    )
  }

  @Test
  fun `Run calculation on a test of prisoner`() {
    val calculation: CalculationResults = webTestClient.post()
      .uri("/calculation/default/test")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculationResults::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.calculatedReleaseDates!!.dates[SLED]).isEqualTo(LocalDate.of(2016, 11, 6))
  }

  @Test
  fun `Run calculation on a DTO sentence`() {
    webTestClient = webTestClient.mutate().responseTimeout(Duration.ofMinutes(5)).build()
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/CRS-1184")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!
    assertThat(calculation.dates[SED]).isEqualTo("2022-11-22")
    assertThat(calculation.dates[MTD]).isEqualTo("2022-02-21")
    assertThat(calculation.dates[TUSED]).isEqualTo("2023-02-21")
    assertThat(calculation.dates[ETD]).isEqualTo("2021-12-21")
    assertThat(calculation.dates[LTD]).isEqualTo("2022-04-21")
  }

  @Test
  fun `Run calculation on A FINE sentence`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/AFINE")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[ARD]).isEqualTo(
      LocalDate.of(2029, 6, 4),
    )
    assertThat(calculation.dates[SED]).isEqualTo(
      LocalDate.of(2029, 6, 4),
    )
  }

  @Test
  fun `Run calculation on EDS recall sentence`() {
    mockManageOffencesClient.noneInPCSC(listOf("A1234BC"))
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/EDSRECALL")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[PRRD]).isEqualTo(
      LocalDate.of(2024, 4, 1),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2024, 4, 1),
    )
  }

  @Test
  fun `User doesnt select any sdsplus sentences`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "SX03001",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      OffencePcscMarkers(
        offenceCode = "SX03005",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      OffencePcscMarkers(
        offenceCode = "SX03005",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      offences = "SX03001,SX03005,SX03007",
    )

    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/SDSPERR")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    webTestClient.post()
      .uri("/calculation/confirm/${calculation.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(
        objectMapper.writeValueAsString(
          SubmitCalculationRequest(
            CalculationFragments("<p>BREAKDOWN</p>"),
            emptyList(),
          ),
        ),
      )
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!
  }

  @Test
  fun `Run relevant remand calculation`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "CS00011",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      OffencePcscMarkers(
        offenceCode = "WR91001",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      offences = "CS00011,WR91001",
    )

    val request = RelevantRemandCalculationRequest(
      listOf(
        RelevantRemand(
          from = LocalDate.of(2021, 1, 1),
          to = LocalDate.of(2021, 1, 31),
          days = 31,
          sentenceSequence = 4,
        ),
      ),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2021, 2, 1),
        bookingId = "RELREM".hashCode().toLong(),
        sequence = 11,
      ),
      calculateAt = LocalDate.of(2021, 2, 1),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREM")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.releaseDate).isEqualTo(LocalDate.of(2021, 4, 1))
    assertThat(calculation.postRecallReleaseDate).isNull()
    assertThat(calculation.validationMessages).isEmpty()
  }

  @Test
  fun `Run relevant remand calculation with more remand than required`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "CS00011",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      OffencePcscMarkers(
        offenceCode = "WR91001",
        pcscMarkers = PcscMarkers(inListA = false, inListB = false, inListC = false, inListD = false),
      ),
      offences = "CS00011,WR91001",
    )

    val request = RelevantRemandCalculationRequest(
      listOf(
        RelevantRemand(
          from = LocalDate.of(2020, 2, 1),
          to = LocalDate.of(2021, 1, 31),
          days = 396,
          sentenceSequence = 4,
        ),
      ),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2021, 2, 1),
        bookingId = "RELREM".hashCode().toLong(),
        sequence = 11,
      ),
      calculateAt = LocalDate.of(2021, 2, 1),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREM")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.releaseDate).isEqualTo(LocalDate.of(2021, 2, 1))
    assertThat(calculation.postRecallReleaseDate).isNull()
    assertThat(calculation.validationMessages).isEmpty()
  }

  @Test
  fun `Run relevant remand calculation with more tagged bail than required`() {
    mockManageOffencesClient.withPCSCMarkersResponse(
      OffencePcscMarkers(
        offenceCode = "CS00011",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      OffencePcscMarkers(
        offenceCode = "WR91001",
        pcscMarkers = PcscMarkers(inListA = true, inListB = true, inListC = true, inListD = true),
      ),
      offences = "CS00011,WR91001",
    )

    val request = RelevantRemandCalculationRequest(
      listOf(),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2021, 2, 1),
        bookingId = "RELREMT".hashCode().toLong(),
        sequence = 11,
      ),
      calculateAt = LocalDate.of(2021, 2, 1),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREMT")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.releaseDate).isEqualTo(LocalDate.of(2021, 2, 1))
    assertThat(calculation.postRecallReleaseDate).isNull()
    assertThat(calculation.validationMessages).isEmpty()
  }

  @Test
  fun `Run relevant remand calculation against a recall`() {
    mockManageOffencesClient.noneInPCSC(listOf("CD71040", "CJ88117"))
    val request = RelevantRemandCalculationRequest(
      listOf(
        RelevantRemand(
          from = LocalDate.of(2021, 1, 1),
          to = LocalDate.of(2021, 1, 31),
          days = 31,
          sentenceSequence = 1,
        ),
      ),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2021, 2, 1),
        bookingId = "RELREMR".hashCode().toLong(),
        recallDate = LocalDate.of(2022, 2, 4),
        sequence = 1,
      ),
      calculateAt = LocalDate.of(2022, 2, 4),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREMR")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.postRecallReleaseDate).isEqualTo(LocalDate.of(2022, 6, 30))
    assertThat(calculation.releaseDate).isEqualTo(LocalDate.of(2021, 9, 30))
    assertThat(calculation.validationMessages).isEmpty()
  }

  @Test
  fun `Run relevant remand calculation which fails validation`() {
    mockManageOffencesClient.noneInPCSC(listOf("CS00011", "WR91001"))
    val request = RelevantRemandCalculationRequest(
      listOf(
        RelevantRemand(
          from = LocalDate.of(2021, 1, 1),
          to = LocalDate.of(2021, 1, 31),
          days = 31,
          sentenceSequence = 4,
        ),
      ),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2021, 2, 1),
        bookingId = "RELREMV".hashCode().toLong(),
        sequence = 4,
      ),
      calculateAt = LocalDate.of(2021, 2, 1),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREMV")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.releaseDate).isNull()
    assertThat(calculation.postRecallReleaseDate).isNull()
    assertThat(calculation.validationMessages).singleElement()
  }

  @Test
  fun `Run relevant remand which has enough calculated remand for immediate release`() {
    val request = RelevantRemandCalculationRequest(
      listOf(
        RelevantRemand(
          from = LocalDate.of(2014, 1, 1),
          to = LocalDate.of(2019, 6, 23),
          days = 2000,
          sentenceSequence = 1,
        ),
      ),
      RelevantRemandSentence(
        sentenceDate = LocalDate.of(2020, 1, 13),
        bookingId = "RELREMI".hashCode().toLong(),
        sequence = 1,
      ),
      calculateAt = LocalDate.of(2020, 1, 13),
    )
    val calculation: RelevantRemandCalculationResult = webTestClient.post()
      .uri("/calculation/relevant-remand/RELREMI")
      .accept(MediaType.APPLICATION_JSON)
      .contentType(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(objectMapper.writeValueAsString(request))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(RelevantRemandCalculationResult::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.releaseDate).isEqualTo(request.sentence.sentenceDate)
    assertThat(calculation.validationMessages).isEmpty()
  }

  @Test
  fun `Run calculation with external movements`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/crs-2107")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel)
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2024, 9, 10),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2025, 9, 14),
    )
  }

  @Test
  fun `Run calculation with ERS return external movements`() {
    val calculation: CalculatedReleaseDates = webTestClient.post()
      .uri("/calculation/ERSRETURN")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .bodyValue(calculationRequestModel.copy(calculationUserInputs = CalculationUserInputs(calculateErsed = true)))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(CalculatedReleaseDates::class.java)
      .returnResult().responseBody!!

    assertThat(calculation.dates[CRD]).isEqualTo(
      LocalDate.of(2024, 1, 8),
    )
    assertThat(calculation.dates[SLED]).isEqualTo(
      LocalDate.of(2026, 12, 29),
    )
    assertThat(calculation.dates[ERSED]).isEqualTo(
      LocalDate.of(2022, 7, 13),
    )
  }

  val calculationRequestModel = CalculationRequestModel(CalculationUserInputs(), 1L)

  companion object {
    const val PRISONER_ID = "default"
    const val PRISONER_ERROR_ID = "123CBA"
    const val PRISONER_ID_SEX_OFFENDER = "S3333XX"
    const val PRISONER_ID_ON_SEX_OFFENDER_REGISTER = "SR"
    val BOOKING_ID = PRISONER_ID.hashCode().toLong()
    val BOOKING_ERROR_ID = PRISONER_ERROR_ID.hashCode().toLong()
    const val BOOKING_ID_DOESNT_EXIST = 92929988L
    const val INACTIVE_PRISONER_ID = "INACTIVE"
  }
}

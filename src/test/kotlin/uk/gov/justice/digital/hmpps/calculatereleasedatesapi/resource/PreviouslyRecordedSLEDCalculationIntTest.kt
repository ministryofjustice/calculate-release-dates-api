package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.MediaType
import org.springframework.test.context.jdbc.Sql
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.IntegrationTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.MockPrisonService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.wiremock.PrisonApiExtension.Companion.prisonApi
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Agency
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationRequestModel
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedCalculationResults
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.GenuineOverrideRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.LatestCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManuallyEnteredDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NomisCalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateHint
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDatesAndCalculationContext
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.LocalDateTime

@SpringBootTest(webEnvironment = RANDOM_PORT, properties = ["feature-toggles.use-adjustments-api=true", "feature-toggles.apply-post-recall-repeal-rules=false"])
@Sql(scripts = ["classpath:/test_data/reset-base-data.sql"])
class PreviouslyRecordedSLEDCalculationIntTest(private val mockPrisonService: MockPrisonService) : IntegrationTestBase() {
  @Autowired
  lateinit var calculationRequestRepository: CalculationRequestRepository

  private val jsonTransformation = JsonTransformation()

  @BeforeEach
  fun setUp() {
    updatedSentencesToVersion("1")

    mockPrisonService.withInstAgencies(
      listOf(
        Agency("ABC", "prison ABC"),
        Agency("HDC4P", "prison HDC4P"),
      ),
    )
    mockPrisonService.withNomisCalculationReasons(
      listOf(
        NomisCalculationReason("NEW", "New Sentence"),
      ),
    )
  }

  @Test
  fun `Can use a previously recorded SLED from a past sentence with a confirmed 2-day calculation based on user inputs`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = TWO_DAY_CHECK_REASON_ID))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )
    updatedSentencesToVersion("2")

    val prelimWithoutPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim without previously recorded SLED",
      prelimWithoutPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 6, 26),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithoutPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc has no previously recorded SLED").isNull()
    val confirmedWithoutPreviouslyRecordedSLED = createConfirmCalculationForPrisoner(prelimWithoutPreviouslyRecordedSLED.calculationRequestId)
    assertDates(
      "confirmed without previously recorded SLED",
      confirmedWithoutPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 6, 26),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(confirmedWithoutPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("confirmed calc has no previously recorded SLED").isNull()

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2025, 9, 12),
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = oldConfirmedCalc.calculationRequestId,
      ),
    )
    val confirmedWithPreviouslyRecordedSLED = createConfirmCalculationForPrisoner(prelimWithPreviouslyRecordedSLED.calculationRequestId)
    assertDates(
      "confirmed using previously recorded SLED",
      confirmedWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(confirmedWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("confirmed calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2025, 9, 12),
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = oldConfirmedCalc.calculationRequestId,
      ),
    )
  }

  @Test
  fun `Don't use a previously recorded SLED if the calculation wasn't a 2 day check`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = INITIAL_CALC_REASON_ID))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )
    updatedSentencesToVersion("2")

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 6, 26),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED not found as it wasn't a 2 day check").isNull()
  }

  @Test
  fun `Can use an equal SED and LED from a manual entry calculation as a previously recorded SLED`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = 9L))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )

    updatedSentencesToVersion("2")

    // Manual calc has later SLED than the original calculation
    val manualCalcResponse = createManualCalculation(
      PRISONER_ID,
      ManualEntryRequest(
        listOf(
          ManuallyEnteredDate(ReleaseDateType.SED, SubmittedDate(13, 10, 2025)),
          ManuallyEnteredDate(ReleaseDateType.LED, SubmittedDate(13, 10, 2025)),
        ),
        TWO_DAY_CHECK_REASON_ID,
        null,
      ),
    )

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 10, 13),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2025, 10, 13),
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = manualCalcResponse.calculationRequestId,
      ),
    )
  }

  @Test
  fun `Don't use SED or LED from a manual entry calculation as a previously recorded SLED if they are not the same date`() {
    createManualCalculation(
      PRISONER_ID,
      ManualEntryRequest(
        listOf(
          ManuallyEnteredDate(ReleaseDateType.SED, SubmittedDate(13, 10, 2025)),
          ManuallyEnteredDate(ReleaseDateType.LED, SubmittedDate(14, 11, 2025)),
        ),
        1L,
        null,
      ),
    )

    updatedSentencesToVersion("2")

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 6, 26),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isNull()
  }

  @Test
  fun `Can use an equal SED and LED from a genuine override calculation as a previously recorded SLED`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = 9L))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )

    updatedSentencesToVersion("2")

    // GO has later SLED than the original calculation
    val newPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = TWO_DAY_CHECK_REASON_ID))
    val genuineOverrideResponse = createGenuineOverride(
      newPreliminaryCalc.calculationRequestId,
      GenuineOverrideRequest(
        dates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 10, 13)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2025, 10, 13)),
        ),
        reason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
        reasonFurtherDetail = null,
      ),
    )

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 10, 13),
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2025, 10, 13),
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = genuineOverrideResponse.newCalculationRequestId!!,
      ),
    )
  }

  @Test
  fun `Does not use SED and LED from a genuine override calculation as a previously recorded SLED if they are not equal`() {
    val newPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    val genuineOverrideResponse = createGenuineOverride(
      newPreliminaryCalc.calculationRequestId,
      GenuineOverrideRequest(
        dates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, LocalDate.of(2025, 10, 13)),
          GenuineOverrideDate(ReleaseDateType.LED, LocalDate.of(2025, 10, 12)),
        ),
        reason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
        reasonFurtherDetail = null,
      ),
    )
    assertThat(genuineOverrideResponse.newCalculationRequestId).isNotNull

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isNull()
  }

  @Test
  fun `Calculations using a previously recorded SLED show hint text and details on the SLED`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = 9L))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)

    updatedSentencesToVersion("2")

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    val confirmedCalcWithOverride = createConfirmCalculationForPrisoner(prelimWithPreviouslyRecordedSLED.calculationRequestId)
    assertThat(confirmedCalcWithOverride.usedPreviouslyRecordedSLED).isNotNull

    stubKeyDates(
      OffenderKeyDates(
        sentenceExpiryDate = confirmedCalcWithOverride.dates[SLED],
        licenceExpiryDate = confirmedCalcWithOverride.dates[SLED],
        reasonCode = "NEW",
        calculatedAt = LocalDateTime.now(),
        comment = "Stub calc for ${confirmedCalcWithOverride.calculationReference}",
        calculatedByUserId = "user1",
        calculatedByFirstName = "User",
        calculatedByLastName = "One",
      ),
    )

    val expectedHint = "SLED from a previous period of custody"
    val releaseDatesResponse = webTestClient.get()
      .uri("/calculation/release-dates/${confirmedCalcWithOverride.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(ReleaseDatesAndCalculationContext::class.java)
      .returnResult().responseBody!!
    assertThat(releaseDatesResponse.dates.find { it.type === SLED }?.hints).containsExactly(ReleaseDateHint(expectedHint))

    val latestCalculation = webTestClient.get()
      .uri("/calculation/$PRISONER_ID/latest")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(LatestCalculation::class.java)
      .returnResult().responseBody!!
    assertThat(latestCalculation.dates.find { it.type === SLED }?.hints).containsExactly(ReleaseDateHint(expectedHint))

    val detailedReleaseDatesResponse = webTestClient.get()
      .uri("/calculation/detailed-results/${confirmedCalcWithOverride.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DetailedCalculationResults::class.java)
      .returnResult().responseBody!!
    assertThat(detailedReleaseDatesResponse.dates[SLED]?.hints).containsExactly(ReleaseDateHint(expectedHint))
    assertThat(detailedReleaseDatesResponse.calculationBreakdown?.breakdownByReleaseDateType[SLED]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        releaseDate = LocalDate.of(2025, 9, 12),
        unadjustedDate = LocalDate.of(2025, 6, 26),
        rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
      ),
    )
    assertThat(detailedReleaseDatesResponse.usedPreviouslyRecordedSLED).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = LocalDate.of(2025, 9, 12),
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = oldConfirmedCalc.calculationRequestId,
      ),
    )
    assertThat(detailedReleaseDatesResponse.context.usePreviouslyRecordedSLEDIfFound).isTrue
  }

  @Test
  fun `Should remove the TUSED if the Previously Recorded SLED is later than then TUSED`() {
    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = 9L))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      LocalDate.of(2025, 9, 12),
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )

    updatedSentencesToVersion("2")

    // GO has later SLED than the newly updated TUSED
    val expectedNewTused = LocalDate.of(2026, 5, 24)
    val sledOverriddenTo = expectedNewTused.plusDays(1)
    val newPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = TWO_DAY_CHECK_REASON_ID))
    val genuineOverrideResponse = createGenuineOverride(
      newPreliminaryCalc.calculationRequestId,
      GenuineOverrideRequest(
        dates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, sledOverriddenTo),
          GenuineOverrideDate(ReleaseDateType.LED, sledOverriddenTo),
        ),
        reason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
        reasonFurtherDetail = null,
      ),
    )

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      sledOverriddenTo,
      LocalDate.of(2025, 5, 24),
      null,
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = sledOverriddenTo,
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = genuineOverrideResponse.newCalculationRequestId!!,
      ),
    )

    val detailedReleaseDatesResponse = webTestClient.get()
      .uri("/calculation/detailed-results/${prelimWithPreviouslyRecordedSLED.calculationRequestId}")
      .accept(MediaType.APPLICATION_JSON)
      .headers(setAuthorisation(roles = listOf("ROLE_RELEASE_DATES_CALCULATOR")))
      .exchange()
      .expectStatus().isOk
      .expectHeader().contentType(MediaType.APPLICATION_JSON)
      .expectBody(DetailedCalculationResults::class.java)
      .returnResult().responseBody!!
    assertThat(detailedReleaseDatesResponse.dates[SLED]?.date).isEqualTo(sledOverriddenTo)
    assertThat(detailedReleaseDatesResponse.dates[TUSED]).isNull()
    assertThat(detailedReleaseDatesResponse.calculationBreakdown?.breakdownByReleaseDateType[SLED]).isEqualTo(
      ReleaseDateCalculationBreakdown(
        releaseDate = sledOverriddenTo,
        unadjustedDate = LocalDate.of(2025, 6, 26),
        rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
      ),
    )
    assertThat(detailedReleaseDatesResponse.calculationBreakdown?.breakdownByReleaseDateType[TUSED]).isNull()
    assertThat(detailedReleaseDatesResponse.usedPreviouslyRecordedSLED).isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = sledOverriddenTo,
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = genuineOverrideResponse.newCalculationRequestId,
      ),
    )
    assertThat(detailedReleaseDatesResponse.context.usePreviouslyRecordedSLEDIfFound).isTrue
  }

  @Test
  fun `Use the most recent two day check rather than the latest SLED date`() {
    val originalCalculatedSLED = LocalDate.of(2025, 9, 12)

    // GO has the later SLED but was performed before the regular calculation
    val goPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = TWO_DAY_CHECK_REASON_ID))
    createGenuineOverride(
      goPreliminaryCalc.calculationRequestId,
      GenuineOverrideRequest(
        dates = listOf(
          GenuineOverrideDate(ReleaseDateType.SED, originalCalculatedSLED.plusDays(1)),
          GenuineOverrideDate(ReleaseDateType.LED, originalCalculatedSLED.plusDays(1)),
        ),
        reason = GenuineOverrideReason.AGGRAVATING_FACTOR_OFFENCE,
        reasonFurtherDetail = null,
      ),
    )

    val oldPreliminaryCalc = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculationReasonId = TWO_DAY_CHECK_REASON_ID))
    val oldConfirmedCalc = createConfirmCalculationForPrisoner(oldPreliminaryCalc.calculationRequestId)
    assertDates(
      "original",
      oldConfirmedCalc.dates,
      originalCalculatedSLED,
      LocalDate.of(2025, 3, 28),
      LocalDate.of(2026, 3, 28),
    )
    updatedSentencesToVersion("2")

    val prelimWithPreviouslyRecordedSLED = createPreliminaryCalculation(PRISONER_ID, CalculationRequestModel(calculationUserInputs = CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculationReasonId = INITIAL_CALC_REASON_ID))
    assertDates(
      "prelim using previously recorded SLED",
      prelimWithPreviouslyRecordedSLED.dates,
      originalCalculatedSLED,
      LocalDate.of(2025, 5, 24),
      LocalDate.of(2026, 5, 24),
    )
    assertThat(prelimWithPreviouslyRecordedSLED.usedPreviouslyRecordedSLED).describedAs("prelim calc with previously recorded SLED").isEqualTo(
      PreviouslyRecordedSLED(
        previouslyRecordedSLEDDate = originalCalculatedSLED,
        calculatedDate = LocalDate.of(2025, 6, 26),
        previouslyRecordedSLEDCalculationRequestId = oldConfirmedCalc.calculationRequestId,
      ),
    )
  }

  private fun assertDates(calcDescription: String, dates: Map<ReleaseDateType, LocalDate?>, expectedSled: LocalDate, expectedCrd: LocalDate, expectedTused: LocalDate?) {
    assertThat(dates[SLED]).describedAs("$calcDescription SLED").isEqualTo(expectedSled)
    assertThat(dates[CRD]).describedAs("$calcDescription CRD").isEqualTo(expectedCrd)
    assertThat(dates[TUSED]).describedAs("$calcDescription TUSED").isEqualTo(expectedTused)
  }

  private fun stubKeyDates(offenderKeyDates: OffenderKeyDates) {
    val bookingId = PRISONER_ID.hashCode().toLong()
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

  private fun updatedSentencesToVersion(version: String) {
    val sentences = jsonTransformation.getAllSentenceAndOffencesJson(version)[PRISONER_ID]!!
    prisonApi.stubGetSentencesAndOffences(PRISONER_ID.hashCode().toLong(), sentences)
  }

  companion object {
    private const val PRISONER_ID = "PrevSLED01"

    private const val INITIAL_CALC_REASON_ID = 1L
    private const val TWO_DAY_CHECK_REASON_ID = 9L
  }
}

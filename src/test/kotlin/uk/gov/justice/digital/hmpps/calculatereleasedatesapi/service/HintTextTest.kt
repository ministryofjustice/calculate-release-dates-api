package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil.Companion.overrideFeatureTogglesForTest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.calculation.CalculationExampleTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcomeHistoricSledOverride
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.SpringTestBase
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import java.io.File
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*
import java.util.stream.Stream

class HintTextTest : SpringTestBase() {

  private val jsonTransformation = JsonTransformation()

  @Autowired
  private lateinit var calculationService: CalculationService

  @Autowired
  private lateinit var calculationTransactionalService: CalculationTransactionalService

  @Autowired
  private lateinit var workingDayService: WorkingDayService

  @Autowired
  private lateinit var featureToggles: FeatureToggles

  @Test
  fun `Historic SLED date`() {
    val historicSledOverride = CalculationOutcomeHistoricSledOverride(
      id = 1,
      calculationRequestId = 1,
      calculationOutcomeDate = LocalDate.now(),
      historicCalculationRequestId = 1,
      historicCalculationOutcomeDate = LocalDate.now(),
    )
    runHintText("crs-2348-sled", historicSledOverride)
  }

  @ParameterizedTest
  @MethodSource(value = ["testCases"])
  fun `Test Hint Texts`(testCase: String) {
    runHintText(testCase, null)
  }

  private fun runHintText(testCase: String, historicSledOverride: CalculationOutcomeHistoricSledOverride?) {
    log.info("Running test-case $testCase")
    val calculationFile = jsonTransformation.loadCalculationTestFile("/hint-text/input-data/$testCase")
    overrideFeatureTogglesForTest(calculationFile, featureToggles)
    val calculation = calculationService.calculateReleaseDates(calculationFile.booking, calculationFile.userInputs)
    val calculatedReleaseDates = createCalculatedReleaseDates(calculation.calculationResult)
    val calculationBreakdown = performCalculationBreakdown(calculationFile.booking, calculatedReleaseDates, calculationFile.userInputs)

    val breakdownWithHints = enrichBreakdownWithHints(
      dates = calculation.calculationResult.dates,
      calculationBreakdown = calculationBreakdown,
      booking = calculationFile.booking,
      historicSledOverride = historicSledOverride,
    )

    val actualDatesAndHints = mapToDatesAndHints(breakdownWithHints)
    val expectedDatesAndHints = jsonTransformation.loadHintTextResults(testCase)

    assertThat(actualDatesAndHints).isEqualTo(expectedDatesAndHints)
  }

  @ParameterizedTest
  @MethodSource(value = ["testCases"])
  fun `Test Hint Text with date overrides`(testCase: String) {
    log.info("Running hint text test-case with date override $testCase")

    val calculationFile = jsonTransformation.loadCalculationTestFile("/hint-text/input-data/$testCase")
    val calculation = calculationService.calculateReleaseDates(calculationFile.booking, calculationFile.userInputs)
    val calculatedReleaseDates = createCalculatedReleaseDates(calculation.calculationResult)
    val calculationBreakdown = performCalculationBreakdown(calculationFile.booking, calculatedReleaseDates, calculationFile.userInputs)
    val breakdownWithHints = enrichBreakdownWithHints(
      dates = calculation.calculationResult.dates,
      calculationBreakdown = calculationBreakdown,
      nomisPrisonerCalculation = OffenderKeyDates(
        reasonCode = "NEW",
        calculatedAt = LocalDateTime.now(),
        homeDetentionCurfewEligibilityDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.HDCED),
        conditionalReleaseDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.CRD),
        licenceExpiryDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.LED) || calculatedReleaseDates.dates.containsKey(ReleaseDateType.SLED),
        sentenceExpiryDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.SED) || calculatedReleaseDates.dates.containsKey(ReleaseDateType.SLED),
        nonParoleDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.NPD),
        automaticReleaseDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.ARD),
        topupSupervisionExpiryDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.TUSED),
        paroleEligibilityDateOverridden = calculatedReleaseDates.dates.containsKey(ReleaseDateType.PED),
      ),
      booking = calculationFile.booking,
      null,
    )

    val actualDatesAndHints = mapToDatesAndHints(breakdownWithHints)
    val expectedDatesAndHints = jsonTransformation.loadHintTextResults(testCase)
      .map { if (it.type in RELEASE_DATE_TYPES_REQUIRING_MANUALLY_OVERRIDDEN_HINT) it.copy(hints = listOf("Manually overridden") + it.hints) else it }

    assertThat(actualDatesAndHints).isEqualTo(expectedDatesAndHints)
  }

  private fun createCalculatedReleaseDates(calculation: CalculationResult): CalculatedReleaseDates = CalculatedReleaseDates(
    dates = calculation.dates,
    calculationRequestId = -1,
    bookingId = -1,
    prisonerId = "",
    calculationStatus = PRELIMINARY,
    calculationReference = UUID.randomUUID(),
    calculationReason = CALCULATION_REASON,
    calculationDate = LocalDate.of(2024, 1, 1),
  )

  private fun performCalculationBreakdown(
    booking: Booking,
    calculatedReleaseDates: CalculatedReleaseDates,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationBreakdown = calculationTransactionalService.calculateWithBreakdown(
    booking,
    calculatedReleaseDates,
    calculationUserInputs,
  )

  private fun enrichBreakdownWithHints(
    dates: Map<ReleaseDateType, LocalDate>,
    calculationBreakdown: CalculationBreakdown,
    nomisPrisonerCalculation: OffenderKeyDates? = null,
    booking: Booking,
    historicSledOverride: CalculationOutcomeHistoricSledOverride? = null,
  ): Map<ReleaseDateType, DetailedDate> {
    val today: LocalDate = LocalDate.now()
    val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
    val nonFridayReleaseService = mock<NonFridayReleaseService>()
    Mockito.`when`(nonFridayReleaseService.getDate(any<ReleaseDate>())).thenAnswer { invocation ->
      val releaseDate = invocation.getArgument<ReleaseDate>(0)
      NonFridayReleaseDay(releaseDate.date)
    }
    val calculationResultEnrichmentService =
      CalculationResultEnrichmentService(nonFridayReleaseService, workingDayService, clock)

    return calculationResultEnrichmentService.addDetailToCalculationDates(
      releaseDates = dates.map { ReleaseDate(date = it.value, type = it.key) },
      sentenceAndOffences = SOURCE_DATA.sentenceAndOffences,
      calculationBreakdown = calculationBreakdown,
      historicalTusedSource = booking.historicalTusedData?.historicalTusedSource,
      nomisPrisonerCalculation = nomisPrisonerCalculation,
      historicSledOverride,
    )
  }

  private fun mapToDatesAndHints(breakdownWithHints: Map<ReleaseDateType, DetailedDate>): List<DatesAndHints> = breakdownWithHints.map {
    DatesAndHints(
      type = it.key,
      date = it.value.date,
      hints = it.value.hints.map { h -> h.text },
    )
  }

  companion object {
    val log: Logger = LoggerFactory.getLogger(this::class.java)

    @JvmStatic
    fun testCases(): Stream<Arguments> {
      val excluded = listOf("crs-2348-sled")
      val dir = File(object {}.javaClass.getResource("/test_data/hint-text/expected-results").file)
      return CalculationExampleTests.Companion.getTestCasesFromDir(dir, excluded, null)
    }

    val CALCULATION_REASON =
      CalculationReason(
        id = -1,
        isActive = true,
        isOther = false,
        displayName = "Reason",
        isBulk = false,
        nomisReason = "UPDATE",
        nomisComment = "NOMIS_COMMENT",
        null,
        false,
      )

    private val SOURCE_DATA = CalculationSourceData(
      emptyList(),
      PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
      AdjustmentsSourceData(
        prisonApiData = BookingAndSentenceAdjustments(
          emptyList(),
          emptyList(),
        ),
      ),
      listOf(),
      null,
    )

    private val RELEASE_DATE_TYPES_REQUIRING_MANUALLY_OVERRIDDEN_HINT = listOf<ReleaseDateType>(
      ReleaseDateType.HDCED,
      ReleaseDateType.CRD,
      ReleaseDateType.LED,
      ReleaseDateType.SED,
      ReleaseDateType.SLED,
      ReleaseDateType.NPD,
      ReleaseDateType.ARD,
      ReleaseDateType.TUSED,
      ReleaseDateType.PED,
    )
  }
}

data class DatesAndHints(
  val type: ReleaseDateType,
  val date: LocalDate,
  val hints: List<String>,
)

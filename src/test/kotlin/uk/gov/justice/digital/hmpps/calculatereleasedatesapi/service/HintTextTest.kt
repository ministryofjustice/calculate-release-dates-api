package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheThreeDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.DetailedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NonFridayReleaseDay
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineAdjustmentService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineAwardedAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalAdmissionMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalReleaseMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSentenceCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheThreeCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineUalAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class HintTextTest {
  private val jsonTransformation = JsonTransformation()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val prisonService = mock<PrisonService>()
  private val eventService = mock<EventService>()
  private val bookingService = mock<BookingService>()
  private val validationService = mock<ValidationService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val approvedDatesSubmissionRepository = mock<ApprovedDatesSubmissionRepository>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val bankHolidayService = mock<BankHolidayService>()
  private val trancheOutcomeRepository = mock<TrancheOutcomeRepository>()

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(BANK_HOLIDAYS)
    Mockito.`when`(nonFridayReleaseService.getDate(any<ReleaseDate>())).thenAnswer { invocation ->
      val releaseDate = invocation.getArgument<ReleaseDate>(0)
      NonFridayReleaseDay(releaseDate.date)
    }
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/hint-text.csv"], numLinesToSkip = 1)
  fun `Test Hint Texts`(testCase: String) {
    log.info("Running test-case $testCase")

    val (booking, calculationUserInputs) = jsonTransformation.loadHintTextBooking(testCase)
    val calculation = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculatedReleaseDates = createCalculatedReleaseDates(calculation.calculationResult)
    val calculationBreakdown = performCalculationBreakdown(booking, calculatedReleaseDates, calculationUserInputs)

    val breakdownWithHints = enrichBreakdownWithHints(calculation.calculationResult.dates, calculationBreakdown)

    val actualDatesAndHints = mapToDatesAndHints(breakdownWithHints)
    val expectedDatesAndHints = jsonTransformation.loadHintTextResults(testCase)

    assertThat(actualDatesAndHints).isEqualTo(expectedDatesAndHints)
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/hint-text.csv"], numLinesToSkip = 1)
  fun `Test Hint Text with date overrides`(testCase: String) {
    log.info("Running hint text test-case with date override $testCase")

    val (booking, calculationUserInputs) = jsonTransformation.loadHintTextBooking(testCase)
    val calculation = calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculatedReleaseDates = createCalculatedReleaseDates(calculation.calculationResult)
    val calculationBreakdown = performCalculationBreakdown(booking, calculatedReleaseDates, calculationUserInputs)
    val breakdownWithHints = enrichBreakdownWithHints(
      calculation.calculationResult.dates,
      calculationBreakdown,
      calculation.calculationResult.dates.map { it.key.name },
    )

    val actualDatesAndHints = mapToDatesAndHints(breakdownWithHints)
    val expectedDatesAndHints = jsonTransformation.loadHintTextResults(testCase)

    assertThat(actualDatesAndHints).isEqualTo(expectedDatesAndHints.map { it.copy(hints = listOf("Manually overridden") + it.hints) })
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
    sentenceOverrideDates: List<String> = emptyList(),
  ): Map<ReleaseDateType, DetailedDate> = calculationResultEnrichmentService.addDetailToCalculationDates(
    releaseDates = dates.map { ReleaseDate(date = it.value, type = it.key) },
    sentenceAndOffences = SOURCE_DATA.sentenceAndOffences,
    calculationBreakdown = calculationBreakdown,
    historicalTusedSource = null,
    sentenceDateOverrides = sentenceOverrideDates,
  )

  private fun mapToDatesAndHints(breakdownWithHints: Map<ReleaseDateType, DetailedDate>): List<DatesAndHints> = breakdownWithHints.map {
    DatesAndHints(
      type = it.key,
      date = it.value.date,
      hints = it.value.hints.map { h -> h.text },
    )
  }

  private val hdcedConfiguration = hdcedConfigurationForTests()
  private val ersedConfiguration = ersedConfigurationForTests()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration, featureToggles = FeatureToggles(hdc365 = true))
  private val ersedCalculator = ErsedCalculator(ersedConfiguration)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
  private val sentencesExtractionService = SentencesExtractionService()
  private val trancheConfiguration = SDS40TrancheConfiguration(sdsEarlyReleaseTrancheOneDate(), sdsEarlyReleaseTrancheTwoDate(), sdsEarlyReleaseTrancheThreeDate())
  private val sentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdcedCalculator, trancheConfiguration)
  private val tranche = Tranche(trancheConfiguration)
  private val trancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
  private val sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(trancheConfiguration)
  private val bookingCalculationService = BookingCalculationService(sentenceIdentificationService)
  private val hdcedExtractionService = HdcedExtractionService(sentencesExtractionService)
  private val bookingExtractionService = BookingExtractionService(
    hdcedExtractionService,
    sentencesExtractionService,
    FixedTermRecallsService(featureToggles = FeatureToggles(revisedFixedTermRecallsRules = true)),
  )
  private val releasePointMultiplierConfigurationForTests = releasePointMultiplierConfigurationForTests()

  private val timelineCalculator = TimelineCalculator(
    sentenceAdjustedCalculationService,
    bookingExtractionService,
  )
  private val timelineAwardedAdjustmentCalculationHandler = TimelineAwardedAdjustmentCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )
  private val timelineSentenceCalculationHandler = TimelineSentenceCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )
  private val timelineTrancheCalculationHandler = TimelineTrancheCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
    trancheAllocationService,
    sentencesExtractionService,
  )
  private val timelineTrancheThreeCalculationHandler = TimelineTrancheThreeCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )
  private val timelineUalAdjustmentCalculationHandler = TimelineUalAdjustmentCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )
  val timelineExternalReleaseMovementCalculationHandler = TimelineExternalReleaseMovementCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )
  val timelineExternalAdmissionMovementCalculationHandler = TimelineExternalAdmissionMovementCalculationHandler(
    trancheConfiguration,
    releasePointMultiplierConfigurationForTests,
    timelineCalculator,
  )

  private val timelineAdjustmentService = TimelineAdjustmentService()

  private val bookingTimelineService = BookingTimelineService(
    workingDayService,
    trancheConfiguration,
    sdsEarlyReleaseDefaultingRulesService,
    timelineCalculator,
    timelineAwardedAdjustmentCalculationHandler,
    timelineTrancheCalculationHandler,
    timelineTrancheThreeCalculationHandler,
    timelineSentenceCalculationHandler,
    timelineUalAdjustmentCalculationHandler,
    timelineExternalReleaseMovementCalculationHandler,
    timelineExternalAdmissionMovementCalculationHandler,
    timelineAdjustmentService,
    featureToggles = FeatureToggles(),
  )
  private val prisonApiDataMapper = PrisonApiDataMapper(TestUtil.objectMapper())
  private val calculationService = CalculationService(
    bookingCalculationService,
    bookingTimelineService,
  )

  private val calculationTransactionalService = CalculationTransactionalService(
    calculationRequestRepository,
    calculationOutcomeRepository,
    calculationReasonRepository,
    TestUtil.objectMapper(),
    prisonService,
    prisonApiDataMapper,
    calculationService,
    bookingService,
    validationService,
    eventService,
    serviceUserService,
    approvedDatesSubmissionRepository,
    nomisCommentService,
    TEST_BUILD_PROPERTIES,
    trancheOutcomeRepository,
  )

  private val today: LocalDate = LocalDate.now()
  private val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
  private val featureToggles = FeatureToggles(sdsEarlyReleaseHints = true, hdc365 = true)

  private val nonFridayReleaseService = mock<NonFridayReleaseService>()

  private val calculationResultEnrichmentService = CalculationResultEnrichmentService(nonFridayReleaseService, workingDayService, clock, featureToggles)

  companion object {
    val BANK_HOLIDAYS =
      BankHolidays(
        RegionBankHolidays(
          "England and Wales",
          listOf(
            BankHoliday("Christmas Day Bank Holiday", LocalDate.of(2021, 12, 27)),
            BankHoliday("Boxing Day Bank Holiday", LocalDate.of(2021, 12, 28)),
          ),
        ),
        RegionBankHolidays("Scotland", emptyList()),
        RegionBankHolidays("Northern Ireland", emptyList()),
      )
    val log: Logger = LoggerFactory.getLogger(this::class.java)
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
      )

    private val SOURCE_DATA = PrisonApiSourceData(
      emptyList(),
      PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
      BookingAndSentenceAdjustments(
        emptyList(),
        emptyList(),
      ),
      listOf(),
      null,
    )
  }
}

data class DatesAndHints(
  val type: ReleaseDateType,
  val date: LocalDate,
  val hints: List<String>,
)

package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvFileSource
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.mock
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
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
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/hint-text.csv"], numLinesToSkip = 1)
  fun `Test Hint Texts`(testCase: String) {
    log.info("Running test-case $testCase")

    val (booking, calculationUserInputs) = jsonTransformation.loadHintTextBooking(testCase)
    val calculation = calculationService.calculateReleaseDates(booking, calculationUserInputs).second
    val calculatedReleaseDates = createCalculatedReleaseDates(calculation)
    calculationService.calculateReleaseDates(booking, calculationUserInputs)
    val calculationBreakdown = performCalculationBreakdown(booking, calculatedReleaseDates, calculationUserInputs)

    val breakdownWithHints = enrichBreakdownWithHints(calculation.dates, calculationBreakdown)

    val actualDatesAndHints = mapToDatesAndHints(breakdownWithHints)
    val expectedDatesAndHints = jsonTransformation.loadHintTextResults(testCase)

    assertThat(actualDatesAndHints).isEqualTo(expectedDatesAndHints)
  }

  private fun createCalculatedReleaseDates(calculation: CalculationResult): CalculatedReleaseDates {
    return CalculatedReleaseDates(
      dates = calculation.dates,
      calculationRequestId = -1,
      bookingId = -1,
      prisonerId = "",
      calculationStatus = PRELIMINARY,
      calculationReference = UUID.randomUUID(),
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.of(2024, 1, 1),
    )
  }

  private fun performCalculationBreakdown(
    booking: Booking,
    calculatedReleaseDates: CalculatedReleaseDates,
    calculationUserInputs: CalculationUserInputs,
  ): CalculationBreakdown =
    calculationTransactionalService.calculateWithBreakdown(
      booking,
      calculatedReleaseDates,
      calculationUserInputs,
    )

  private fun enrichBreakdownWithHints(
    dates: Map<ReleaseDateType, LocalDate>,
    calculationBreakdown: CalculationBreakdown,
  ): Map<ReleaseDateType, DetailedDate> =
    calculationResultEnrichmentService.addDetailToCalculationDates(
      releaseDates = dates.map { ReleaseDate(date = it.value, type = it.key) },
      sentenceAndOffences = SOURCE_DATA.sentenceAndOffences,
      calculationBreakdown = calculationBreakdown,
      historicalTusedSource = null,
    )

  private fun mapToDatesAndHints(breakdownWithHints: Map<ReleaseDateType, DetailedDate>): List<DatesAndHints> =
    breakdownWithHints.map {
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
  private val sentenceAggregator = SentenceAggregator()
  private val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultiplierConfigurationForTests())
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val ersedCalculator = ErsedCalculator(ersedConfiguration)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
  private val sentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService, releasePointMultiplierLookup, sentenceAggregator)
  private val sentencesExtractionService = SentencesExtractionService()
  private val sentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdcedCalculator)
  private val trancheConfiguration = SDS40TrancheConfiguration(sdsEarlyReleaseTrancheOneDate(), sdsEarlyReleaseTrancheTwoDate())
  private val trancheOne = TrancheOne(trancheConfiguration)
  private val trancheTwo = TrancheTwo(trancheConfiguration)
  private val trancheAllocationService = TrancheAllocationService(trancheOne, trancheTwo, trancheConfiguration)
  private val sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(sentencesExtractionService, trancheConfiguration)
  private val bookingCalculationService = BookingCalculationService(sentenceCalculationService, sentenceIdentificationService)
  private val hdcedExtractionService = HdcedExtractionService(sentencesExtractionService)
  private val bookingExtractionService = BookingExtractionService(hdcedExtractionService, sentencesExtractionService)
  private val bookingTimelineService = BookingTimelineService(
    sentenceAdjustedCalculationService,
    sentencesExtractionService,
    workingDayService,
    sdsEarlyReleaseTrancheOneDate(),
  )
  private val prisonApiDataMapper = PrisonApiDataMapper(TestUtil.objectMapper())
  private val calculationService = CalculationService(
    bookingCalculationService,
    bookingExtractionService,
    bookingTimelineService,
    sdsEarlyReleaseDefaultingRulesService,
    trancheAllocationService,
    sentencesExtractionService,
    trancheConfiguration,
    TestUtil.objectMapper(),
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

  private val today: LocalDate = LocalDate.of(2000, 1, 1)
  private val clock = Clock.fixed(today.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())
  private val featureToggles = FeatureToggles(sdsEarlyReleaseHints = true)

  private val nonFridayReleaseService = NonFridayReleaseService(bankHolidayService, clock)

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
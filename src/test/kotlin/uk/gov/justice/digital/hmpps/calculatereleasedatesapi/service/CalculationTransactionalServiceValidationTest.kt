package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import nl.altindag.log.LogCaptor
import org.joda.time.DateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdced4ConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest.Companion.BOOKING
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest.Companion.cachedBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class CalculationTransactionalServiceValidationTest {
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val prisonService = mock<PrisonService>()
  private val eventService = mock<EventService>()
  private val bookingService = mock<BookingService>()
  private val calculationService = mock<CalculationService>()
  private val validationService = mock<ValidationService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val approvedDatesSubmissionRepository = mock<ApprovedDatesSubmissionRepository>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val bankHolidayService = mock<BankHolidayService>()
  private val trancheOutcomeRepository = mock<TrancheOutcomeRepository>()

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(cachedBankHolidays)
  }

  @Test
  fun `fullValidation logs expected messages`() {
    val logCaptor = LogCaptor.forClass(CalculationTransactionalService::class.java)

    val prisonerId = "A1234BC"
    val calculationUserInputs = CalculationUserInputs()
    val fakeMessages = listOf<ValidationMessage>()

    // Mocking the behaviour of services
    whenever(prisonService.getPrisonApiSourceData(prisonerId, true)).thenReturn(fakeSourceData)
    whenever(validationService.validateBeforeCalculation(any(), eq(calculationUserInputs))).thenReturn(fakeMessages)
    whenever(bookingService.getBooking(any(), eq(calculationUserInputs))).thenReturn(BOOKING)
    whenever(calculationService.calculateReleaseDates(any(), eq(calculationUserInputs), eq(true))).thenReturn(
      Pair(
        BOOKING,
        mock<CalculationResult>(),
      ),
    )
    whenever(calculationService.calculateReleaseDates(any(), eq(calculationUserInputs), eq(false))).thenReturn(
      Pair(
        BOOKING,
        mock<CalculationResult>(),
      ),
    )
    whenever(validationService.validateBookingAfterCalculation(any(), any())).thenReturn(fakeMessages)

    // Call the method under test
    calculationTransactionalService().fullValidation(prisonerId, calculationUserInputs)
    // Get the captured log messages
    val logMessages = logCaptor.infoLogs

    // Verify the expected log messages
    assertEquals(
      listOf(
        "Full Validation for $prisonerId",
        "Gathering source data from PrisonAPI",
        "Source data: $fakeSourceData",
        "Stage 1: Running initial calculation validations",
        "Initial validation passed",
        "Retrieving booking information",
        "Booking information: $BOOKING",
        "Stage 2: Running booking-related calculation validations",
        "Booking validation passed",
        "Stage 3: Calculating release dates",
        "Release dates calculated",
        "Calculating release dates for longest possible sentences",
        "Longest possible release dates calculated",
        "Stage 4: Running final booking validation after calculation",
        fakeMessages.joinToString("\n"),
      ),
      logMessages,
    )
  }

  private fun calculationTransactionalService(
    params: String = "calculation-params",
    overriddenConfigurationParams: Map<String, Any> = emptyMap(),
    passedInServices: List<String> = emptyList(),
  ): CalculationTransactionalService {
    val hdcedConfiguration =
      hdcedConfigurationForTests() // HDCED and ERSED params not currently overridden in alt-calculation-params
    var hdced4Configuration = hdced4ConfigurationForTests()

    hdced4Configuration = if (overriddenConfigurationParams.containsKey("hdc4CommencementDate")) {
      val overwrittenHdced4Config =
        hdced4Configuration.copy(hdc4CommencementDate = overriddenConfigurationParams["hdc4CommencementDate"] as Date)
      overwrittenHdced4Config
    } else {
      // If using alternate release config then set the HDC4 commencement date to tomorrow
      val overwrittenHdced4Config = hdced4Configuration.copy(hdc4CommencementDate = DateTime.now().plusDays(1).toDate())
      overwrittenHdced4Config
    }

    val ersedConfiguration = ersedConfigurationForTests()
    val releasePointMultipliersConfiguration = releasePointMultiplierConfigurationForTests(params)

    val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
    val workingDayService = WorkingDayService(bankHolidayService)
    val tusedCalculator = TusedCalculator(workingDayService)
    val sentenceAggregator = SentenceAggregator()
    val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultipliersConfiguration)

    val hdced4Calculator = Hdced4Calculator(hdcedConfiguration, sentenceAggregator, releasePointMultiplierLookup)
    val ersedCalculator = ErsedCalculator(ersedConfiguration)
    val sentenceAdjustedCalculationService =
      SentenceAdjustedCalculationService(hdcedCalculator, tusedCalculator, hdced4Calculator, ersedCalculator)
    val sentenceCalculationService =
      SentenceCalculationService(sentenceAdjustedCalculationService, releasePointMultiplierLookup, sentenceAggregator)
    val sentencesExtractionService = SentencesExtractionService()
    val sentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdced4Calculator)
    val sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(sentencesExtractionService)
    val bookingCalculationService = BookingCalculationService(
      sentenceCalculationService,
      sentenceIdentificationService,
    )
    val bookingExtractionService = BookingExtractionService(
      sentencesExtractionService,
      hdcedConfiguration,
      hdced4Configuration,
    )
    val bookingTimelineService = BookingTimelineService(
      sentenceAdjustedCalculationService,
      sentencesExtractionService,
      workingDayService,
      sdsEarlyReleaseTrancheOneDate(),
    )

    val trancheOne = TrancheOne(sdsEarlyReleaseTrancheOneDate(params), sdsEarlyReleaseTrancheTwoDate(params))
    val trancheTwo = TrancheTwo(sdsEarlyReleaseTrancheTwoDate(params))

    val trancheAllocationService = TrancheAllocationService(TrancheOne(sdsEarlyReleaseTrancheOneDate(), sdsEarlyReleaseTrancheTwoDate()), TrancheTwo(sdsEarlyReleaseTrancheOneDate()))
    val prisonApiDataMapper = PrisonApiDataMapper(TestUtil.objectMapper())

    val calculationService = CalculationService(
      bookingCalculationService,
      bookingExtractionService,
      bookingTimelineService,
      sdsEarlyReleaseDefaultingRulesService,
      trancheAllocationService,
      trancheOne,
      trancheTwo,
      sentencesExtractionService,
      TestUtil.objectMapper(),
    )

    val validationServiceToUse = if (passedInServices.contains(ValidationService::class.java.simpleName)) {
      getActiveValidationService(sentencesExtractionService, trancheOne)
    } else {
      validationService
    }

    return CalculationTransactionalService(
      calculationRequestRepository,
      calculationOutcomeRepository,
      calculationReasonRepository,
      TestUtil.objectMapper(),
      prisonService,
      prisonApiDataMapper,
      calculationService,
      bookingService,
      validationServiceToUse,
      eventService,
      serviceUserService,
      approvedDatesSubmissionRepository,
      nomisCommentService,
      TEST_BUILD_PROPERTIES,
      trancheOutcomeRepository,
    )
  }

  private val fakeSourceData = PrisonApiSourceData(
    emptyList(),
    PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
    BookingAndSentenceAdjustments(
      emptyList(),
      emptyList(),
    ),
    listOf(),
    null,
  )

  private fun getActiveValidationService(sentencesExtractionService: SentencesExtractionService, trancheOne: TrancheOne): ValidationService {
    return ValidationService(sentencesExtractionService, featureToggles = FeatureToggles(true, true, false), trancheOne)
  }
}

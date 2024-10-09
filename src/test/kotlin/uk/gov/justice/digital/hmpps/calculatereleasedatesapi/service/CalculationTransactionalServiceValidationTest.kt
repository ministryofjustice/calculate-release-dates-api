package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import nl.altindag.log.LogCaptor
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheOneDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.sdsEarlyReleaseTrancheTwoDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.AdjustmentValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.BotusValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.DtoValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.EDSValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.FineValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.PreCalculationValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.RecallValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SOPCValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.Section91ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SentenceValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.UnsupportedValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate

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
  private val objectMapper = TestUtil.objectMapper()

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

    val sourceDataJson = objectMapper.writeValueAsString(fakeSourceData)
    val bookingJson = objectMapper.writeValueAsString(BOOKING)

    // Verify the expected log messages
    assertEquals(
      listOf(
        "Full Validation for $prisonerId",
        "Gathering source data from PrisonAPI",
        "Source data:\n$sourceDataJson",
        "Stage 1: Running initial calculation validations",
        "Initial validation passed",
        "Retrieving booking information",
        "Booking information:\n$bookingJson",
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

    val ersedConfiguration = ersedConfigurationForTests()
    val releasePointMultipliersConfiguration = releasePointMultiplierConfigurationForTests(params)

    val workingDayService = WorkingDayService(bankHolidayService)
    val tusedCalculator = TusedCalculator(workingDayService)
    val sentenceAggregator = SentenceAggregator()
    val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultipliersConfiguration)

    val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
    val ersedCalculator = ErsedCalculator(ersedConfiguration)
    val sentenceAdjustedCalculationService =
      SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
    val sentenceCalculationService =
      SentenceCalculationService(sentenceAdjustedCalculationService, releasePointMultiplierLookup, sentenceAggregator)
    val sentencesExtractionService = SentencesExtractionService()
    val sentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdcedCalculator)
    val trancheConfiguration = SDS40TrancheConfiguration(sdsEarlyReleaseTrancheOneDate(params), sdsEarlyReleaseTrancheTwoDate(params))
    val trancheOne = TrancheOne(trancheConfiguration)
    val trancheTwo = TrancheTwo(trancheConfiguration)
    val trancheAllocationService = TrancheAllocationService(trancheOne, trancheTwo, trancheConfiguration)
    val sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(sentencesExtractionService, trancheConfiguration)
    val hdcedExtractionService = HdcedExtractionService(
      sentencesExtractionService,
    )

    val bookingCalculationService = BookingCalculationService(
      sentenceCalculationService,
      sentenceIdentificationService,
    )
    val bookingExtractionService = BookingExtractionService(
      hdcedExtractionService,
      sentencesExtractionService,
    )
    val bookingTimelineService = BookingTimelineService(
      sentenceAdjustedCalculationService,
      sentencesExtractionService,
      workingDayService,
      sdsEarlyReleaseTrancheOneDate(),
    )

    val prisonApiDataMapper = PrisonApiDataMapper(TestUtil.objectMapper())

    val calculationService = CalculationService(
      bookingCalculationService,
      bookingExtractionService,
      bookingTimelineService,
      sdsEarlyReleaseDefaultingRulesService,
      trancheAllocationService,
      sentencesExtractionService,
      trancheConfiguration,
      TestUtil.objectMapper(),
    )

    val validationServiceToUse = if (passedInServices.contains(ValidationService::class.java.simpleName)) {
      getActiveValidationService(sentencesExtractionService, trancheConfiguration)
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

  private fun getActiveValidationService(sentencesExtractionService: SentencesExtractionService, trancheConfiguration: SDS40TrancheConfiguration): ValidationService {
    val featureToggles = FeatureToggles(true, true, false)
    val validationUtilities = ValidationUtilities()
    val fineValidationService = FineValidationService(validationUtilities)
    val adjustmentValidationService = AdjustmentValidationService(trancheConfiguration)
    val dtoValidationService = DtoValidationService()
    val botusValidationService = BotusValidationService()
    val recallValidationService = RecallValidationService(trancheConfiguration)
    val unsupportedValidationService = UnsupportedValidationService()
    val section91ValidationService = Section91ValidationService(validationUtilities)
    val sopcValidationService = SOPCValidationService(validationUtilities)
    val edsValidationService = EDSValidationService(validationUtilities)
    val sentenceValidationService = SentenceValidationService(
      validationUtilities,
      sentencesExtractionService,
      section91ValidationService = section91ValidationService,
      sopcValidationService = sopcValidationService,
      fineValidationService,
      edsValidationService = edsValidationService,
    )
    val preCalculationValidationService = PreCalculationValidationService(
      featureToggles = featureToggles,
      fineValidationService = fineValidationService,
      adjustmentValidationService = adjustmentValidationService,
      dtoValidationService = dtoValidationService,
      botusValidationService = botusValidationService,
      unsupportedValidationService = unsupportedValidationService,
    )

    return ValidationService(
      preCalculationValidationService = preCalculationValidationService,
      adjustmentValidationService = adjustmentValidationService,
      recallValidationService = recallValidationService,
      sentenceValidationService = sentenceValidationService,
      validationUtilities = validationUtilities,
    )
  }
}

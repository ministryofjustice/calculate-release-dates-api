package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import nl.altindag.log.LogCaptor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.mock
import org.springframework.boot.info.BuildProperties
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.ersedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.hdcedConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.CalculationParamsTestConfigHelper.releasePointMultiplierConfigurationForTests
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.SDS40TrancheConfiguration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.CalculationTransactionalServiceTest.Companion.cachedBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.AdjustmentValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.BotusValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.DtoValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.EDSValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.FineValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.PostCalculationValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.PreCalculationValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.RecallValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SHPOValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SOPCValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.Section91ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SentenceValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ToreraValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.UnsupportedValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate

class CalculationTransactionServiceFromBookingValidationTest {

  private val ersedConfiguration = ersedConfigurationForTests()
  private val hdcedConfiguration = hdcedConfigurationForTests()
  private val releasePointMultipliersConfiguration = releasePointMultiplierConfigurationForTests("calculation-params")
  private val tranche = Tranche(TRANCHE_CONFIGURATION)
  private val trancheAllocationService = TrancheAllocationService(tranche, TRANCHE_CONFIGURATION)

  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val ersedCalculator = ErsedCalculator(ersedConfiguration)

  private val sentenceAggregator = SentenceAggregator()
  private val releasePointMultiplierLookup = ReleasePointMultiplierLookup(releasePointMultipliersConfiguration)
  private val sentenceExtractionService = SentencesExtractionService()
  private val hdcedExtractionService = HdcedExtractionService(sentenceExtractionService)
  private val bookingExtractionService = BookingExtractionService(
    hdcedExtractionService = hdcedExtractionService,
    extractionService = sentenceExtractionService,
  )
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
  private val bookingTimelineService = BookingTimelineService(
    sentenceAdjustedCalculationService,
    sentenceExtractionService,
    workingDayService = workingDayService,
    trancheOneCommencementDate = TRANCHE_CONFIGURATION.trancheOneCommencementDate,
  )
  private val objectMapper = TestUtil.objectMapper()

  private val manageOffencesApiClient = mock<ManageOffencesApiClient>()
  private val manageOffencesService = ManageOffencesService(
    manageOffencesApiClient = manageOffencesApiClient,
  )
  private val calculationService = CalculationService(
    objectMapper = objectMapper,
    trancheConfiguration = TRANCHE_CONFIGURATION,
    bookingCalculationService = BookingCalculationService(
      sentenceCalculationService = SentenceCalculationService(
        sentenceAdjustedCalculationService,
        releasePointMultiplierLookup,
        sentenceAggregator,
      ),
      sentenceIdentificationService = SentenceIdentificationService(
        tusedCalculator = tusedCalculator,
        hdcedCalculator = hdcedCalculator,
      ),
    ),
    bookingExtractionService = bookingExtractionService,
    bookingTimelineService = bookingTimelineService,
    sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(
      sentenceExtractionService,
      trancheConfiguration = TRANCHE_CONFIGURATION,
    ),
    trancheAllocationService = trancheAllocationService,
    extractionService = sentenceExtractionService,
  )

  private val validationService = getActiveValidationService(
    trancheConfiguration = TRANCHE_CONFIGURATION,
    sentencesExtractionService = SentencesExtractionService(),
  )
  private val calculationTransactionalService = CalculationTransactionalService(
    calculationRequestRepository = mock<CalculationRequestRepository>(),
    calculationOutcomeRepository = mock<CalculationOutcomeRepository>(),
    calculationReasonRepository = mock<CalculationReasonRepository>(),
    objectMapper = objectMapper,
    prisonService = mock<PrisonService>(),
    prisonApiDataMapper = mock<PrisonApiDataMapper>(),
    calculationService = calculationService,
    bookingService = mock<BookingService>(),
    validationService = validationService,
    eventService = mock<EventService>(),
    serviceUserService = mock<ServiceUserService>(),
    approvedDatesSubmissionRepository = mock<ApprovedDatesSubmissionRepository>(),
    nomisCommentService = mock<NomisCommentService>(),
    buildProperties = mock<BuildProperties>(),
    trancheOutcomeRepository = mock<TrancheOutcomeRepository>(),
  )

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(cachedBankHolidays)
  }

  @Test
  fun `fullValidationFromBooking runs as expected messages`() {
    val logCaptor = LogCaptor.forClass(CalculationTransactionalService::class.java)
    logCaptor.setLogLevelToTrace()

    val calculationUserInputs = CalculationUserInputs()
    val fakeMessages = listOf<ValidationMessage>()

    val booking = loadBooking("CRS-2165")
    val bookingJson = objectMapper.writeValueAsString(booking)

    calculationTransactionalService.fullValidationFromBookingData(booking, calculationUserInputs)

    val infoLogMessages = logCaptor.infoLogs
    val traceLogMessages = logCaptor.traceLogs

    assertEquals(
      listOf(
        "Stage 2: Running booking-related calculation validations",
        "Booking validation passed",
        "Stage 3: Calculating release dates",
        "Release dates calculated",
        "Calculating release dates for longest possible sentences",
        "Longest possible release dates calculated",
        "Stage 4: Running final booking validation after calculation",
        fakeMessages.joinToString("\n"),
      ),
      infoLogMessages,
    )

    assertEquals(
      listOf(
        "Booking information: $bookingJson",
      ),
      traceLogMessages,
    )
  }

  private fun getActiveValidationService(
    sentencesExtractionService: SentencesExtractionService,
    trancheConfiguration: SDS40TrancheConfiguration,
  ): ValidationService {
    val featureToggles = FeatureToggles(true, true, false, sds40ConsecutiveManualJourney = true)
    val validationUtilities = ValidationUtilities()
    val fineValidationService = FineValidationService(validationUtilities)
    val adjustmentValidationService = AdjustmentValidationService(trancheConfiguration)
    val dtoValidationService = DtoValidationService()
    val botusValidationService = BotusValidationService()
    val recallValidationService = RecallValidationService(trancheConfiguration)
    val unsupportedValidationService = UnsupportedValidationService()
    val postCalculationValidationService = PostCalculationValidationService(trancheConfiguration, featureToggles)
    val section91ValidationService = Section91ValidationService(validationUtilities)
    val sopcValidationService = SOPCValidationService(validationUtilities)
    val edsValidationService = EDSValidationService(validationUtilities)
    val toreraValidationService = ToreraValidationService(manageOffencesService)
    val sentenceValidationService = SentenceValidationService(
      validationUtilities,
      sentencesExtractionService,
      section91ValidationService = section91ValidationService,
      sopcValidationService = sopcValidationService,
      fineValidationService = fineValidationService,
      edsValidationService = edsValidationService,
    )
    val preCalculationValidationService = PreCalculationValidationService(
      featureToggles = featureToggles,
      fineValidationService = fineValidationService,
      adjustmentValidationService = adjustmentValidationService,
      dtoValidationService = dtoValidationService,
      botusValidationService = botusValidationService,
      unsupportedValidationService = unsupportedValidationService,
      toreraValidationService = toreraValidationService,
    )

    return ValidationService(
      preCalculationValidationService = preCalculationValidationService,
      adjustmentValidationService = adjustmentValidationService,
      recallValidationService = recallValidationService,
      sentenceValidationService = sentenceValidationService,
      validationUtilities = validationUtilities,
      postCalculationValidationService = postCalculationValidationService,
      shpoValidationService = SHPOValidationService(),
    )
  }

  private fun loadBooking(testData: String): Booking {
    val jsonTransformation = JsonTransformation()
    val json = jsonTransformation.getJsonTest("$testData.json", "booking/build_consecutive_sentences")
    return objectMapper.readValue(json, Booking::class.java)
  }

  private companion object {
    val TRANCHE_CONFIGURATION = SDS40TrancheConfiguration(LocalDate.of(2024, 9, 10), LocalDate.of(2024, 10, 22))
  }
}

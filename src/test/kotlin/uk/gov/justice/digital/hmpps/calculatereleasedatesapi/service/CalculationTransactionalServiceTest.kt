package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import com.fasterxml.jackson.databind.JsonNode
import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import jakarta.persistence.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.fail
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Captor
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDatesSubmission
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.APD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ERSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCAD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ROTL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationDataHasChangedError
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeHistoricOverrideRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.TrancheOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceAdjustedCalculationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceCombinationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelineCalculator
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.TimelinePostTrancheAdjustmentService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineAwardedAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalAdmissionMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineExternalReleaseMovementCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineSentenceCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineTrancheThreeCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.handlers.TimelineUalAdjustmentCalculationHandler
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.AdjustmentValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.BotusValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.DateValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.DtoValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.EDSValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.FineValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.PostCalculationValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.PreCalculationValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.RecallValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SOPCValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.Section91ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.SentenceValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ToreraValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.UnsupportedValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.io.File
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.Optional
import java.util.UUID
import java.util.stream.Stream

@ExtendWith(MockitoExtension::class)
class CalculationTransactionalServiceTest {
  private val jsonTransformation = JsonTransformation()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val calculationOutcomeHistoricOverrideRepository = mock<CalculationOutcomeHistoricOverrideRepository>()
  private val dominantHistoricDateService = DominantHistoricDateService()
  private val manageOffencesService = mock<ManageOffencesService>()
  private val prisonService = mock<PrisonService>()
  private val calculationSourceDataService = mock<CalculationSourceDataService>()
  private val eventService = mock<EventService>()
  private val bookingService = mock<BookingService>()
  private val validationService = mock<ValidationService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val approvedDatesSubmissionRepository = mock<ApprovedDatesSubmissionRepository>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val bankHolidayService = mock<BankHolidayService>()
  private val trancheOutcomeRepository = mock<TrancheOutcomeRepository>()
  private val fixedTermRecallsService = FixedTermRecallsService()
  private val calculationConfirmationService = CalculationConfirmationService(
    calculationRequestRepository,
    serviceUserService,
    nomisCommentService,
    prisonService,
    eventService,
    approvedDatesSubmissionRepository,
  )

  private val fakeSourceData = CalculationSourceData(
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

  private fun defaultParams(params: String?): String {
    if (params == null) {
      return "calculation-params"
    }
    return params
  }

  @Captor
  lateinit var updatedOffenderDatesArgumentCaptor: ArgumentCaptor<UpdateOffenderDates>

  @ParameterizedTest
  @MethodSource(value = ["testCaseSource"])
  fun `Test Example`(
    example: String,
  ) {
    log.info("Testing example $example")
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    val calculationTestFile = jsonTransformation.loadCalculationTestFile("$example")
    val calculatedReleaseDates: CalculationOutput
    val returnedValidationMessages: List<ValidationMessage>
    try {
      calculatedReleaseDates = calculationService(
        defaultParams(calculationTestFile.params),
        passedInServices = listOf(ValidationService::class.java.simpleName),
      )
        .calculateReleaseDates(calculationTestFile.booking, calculationTestFile.userInputs)
      val sentencesExtractionService = SentencesExtractionService()
      val trancheConfiguration = SDS40TrancheConfiguration(
        sdsEarlyReleaseTrancheOneDate(defaultParams(calculationTestFile.params)),
        sdsEarlyReleaseTrancheTwoDate(defaultParams(calculationTestFile.params)),
        sdsEarlyReleaseTrancheThreeDate(defaultParams(calculationTestFile.params)),
      )
      val featureToggles = parseFeatureToggles(calculationTestFile.featureTogglesStr)
      val myValidationService = getActiveValidationService(sentencesExtractionService, trancheConfiguration, featureToggles)

      returnedValidationMessages = myValidationService.validateBookingAfterCalculation(
        calculatedReleaseDates,
        calculationTestFile.booking,
      ).distinct()
    } catch (e: Exception) {
      if (!calculationTestFile.error.isNullOrEmpty()) {
        assertEquals(calculationTestFile.error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    log.info(
      "Example $example outcome BookingCalculation: {}",
      TestUtil.objectMapper().writeValueAsString(calculatedReleaseDates),
    )
    if (calculationTestFile.expectedValidationException != null) {
      val expectedExceptions = calculationTestFile.expectedValidationException.split("|")
      assertThat(returnedValidationMessages).hasSize(expectedExceptions.size)
      expectedExceptions.forEachIndexed { index, exception ->
        assertThat(returnedValidationMessages[index].code.toString()).isEqualTo(exception)
        calculationTestFile.expectedValidationMessage?.let { assertThat(returnedValidationMessages[index].message).isEqualTo(it) }
      }
    } else if (returnedValidationMessages.isNotEmpty()) {
      fail("Validation messages were returned: $returnedValidationMessages")
    } else {
      val bookingData = jsonTransformation.loadCalculationResult("$example")
      val result = bookingData.first
      assertEquals(
        result.dates.entries.sortedBy { it.key },
        calculatedReleaseDates.calculationResult.dates.entries.sortedBy { it.key },
      )
      assertEquals(result.effectiveSentenceLength, calculatedReleaseDates.calculationResult.effectiveSentenceLength)
      if (calculationTestFile.assertSds40 == true) {
        assertEquals(result.affectedBySds40, calculatedReleaseDates.calculationResult.affectedBySds40)
      }
      if (bookingData.second.contains("sdsEarlyReleaseAllocatedTranche")) {
        assertEquals(
          result.sdsEarlyReleaseAllocatedTranche,
          calculatedReleaseDates.calculationResult.sdsEarlyReleaseAllocatedTranche,
        )
        assertEquals(result.sdsEarlyReleaseTranche, calculatedReleaseDates.calculationResult.sdsEarlyReleaseTranche)
      }
    }
  }

  @ParameterizedTest
  @MethodSource(value = ["breakdownTestCaseSource"])
  fun `Test UX Example Breakdowns`(
    example: String,
  ) {
    log.info("Testing example $example")
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    val calculationTestFile = jsonTransformation.loadCalculationTestFile("$example")
    val calculation = jsonTransformation.loadCalculationResult("$example").first

    val calculationBreakdown: CalculationBreakdown?
    try {
      calculationBreakdown = calculationTransactionalService(defaultParams(calculationTestFile.params)).calculateWithBreakdown(
        calculationTestFile.booking,
        CalculatedReleaseDates(
          calculation.dates,
          -1,
          -1,
          "",
          PRELIMINARY,
          calculationReference = UUID.randomUUID(),
          calculationReason = CALCULATION_REASON,
          calculationDate = LocalDate.of(2024, 1, 1),
        ),
        calculationTestFile.userInputs,
      )
    } catch (e: Exception) {
      if (!calculationTestFile.error.isNullOrEmpty()) {
        assertEquals(calculationTestFile.error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    log.info(
      "Example $example outcome CalculationBreakdown: {}",
      TestUtil.objectMapper().writeValueAsString(calculationBreakdown),
    )
    val actualJson: String? = TestUtil.objectMapper().writeValueAsString(calculationBreakdown)
    val expectedJson: String =
      jsonTransformation.getJsonTest("$example.json", "calculation_breakdown_response")

    JSONAssert.assertEquals(
      expectedJson,
      actualJson,
      JSONCompareMode.LENIENT,
    )

    assertThat(calculationBreakdown).isEqualTo(jsonTransformation.loadCalculationBreakdown("$example"))
  }

  @Test
  fun `Test fetching calculation results by requestId`() {
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )

    val bookingCalculation = calculationTransactionalService().findCalculationResults(CALCULATION_REQUEST_ID)

    assertEquals(
      bookingCalculation,
      BOOKING_CALCULATION,
    )
  }

  @Test
  fun `Test validation of a confirm calculation request fails if the booking data has changed since the PRELIM calc`() {
    whenever(
      calculationRequestRepository.findByIdAndCalculationStatus(
        CALCULATION_REQUEST_ID,
        PRELIMINARY.name,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<PreconditionFailedException> {
      calculationTransactionalService().validateAndConfirmCalculation(
        CALCULATION_REQUEST_ID,
        SubmitCalculationRequest(calculationFragments = CalculationFragments(""), approvedDates = null),
      )
    }
    assertThat(exception)
      .isInstanceOf(PreconditionFailedException::class.java)
      .withFailMessage("The booking data used for the preliminary calculation has changed")
  }

  @Test
  fun `Test validation of a confirm calculation request fails if there is no PRELIM calc`() {
    whenever(
      calculationRequestRepository.findByIdAndCalculationStatus(
        CALCULATION_REQUEST_ID,
        PRELIMINARY.name,
      ),
    ).thenReturn(Optional.empty())
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<EntityNotFoundException> {
      calculationTransactionalService().validateAndConfirmCalculation(
        CALCULATION_REQUEST_ID,
        SubmitCalculationRequest(calculationFragments = CalculationFragments(""), approvedDates = null),
      )
    }
    assertThat(exception)
      .isInstanceOf(EntityNotFoundException::class.java)
      .withFailMessage("No preliminary calculation exists for calculationRequestId $CALCULATION_REQUEST_ID")
  }

  @Test
  fun `Test validation succeeds if the PRELIMINARY calculation matches the one being confirmed`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(CALCULATION_REQUEST_ID, PRELIMINARY.name))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)))
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )

    assertDoesNotThrow {
      calculationTransactionalService().validateAndConfirmCalculation(
        CALCULATION_REQUEST_ID,
        SubmitCalculationRequest(calculationFragments = CalculationFragments(""), approvedDates = null),
      )
    }
  }

  @Test
  fun `Test that write to NOMIS and publishing event succeeds`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA),
      ),
    )
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")

    calculationConfirmationService.writeToNomisAndPublishEvent(
      PRISONER_ID,
      BOOKING.copy(sentences = listOf(StandardSENTENCE.copy(duration = ZERO_DURATION))),
      BOOKING_CALCULATION.copy(
        dates = mutableMapOf(
          CRD to CALCULATION_OUTCOME_CRD.outcomeDate!!,
          SED to THIRD_FEB_2021,
          ERSED to FIFTH_APRIL_2021,
          ESED to ESED_DATE,
        ),
        effectiveSentenceLength = Period.of(6, 2, 3),
      ),
      emptyList(),
    )

    verify(prisonService).postReleaseDates(
      BOOKING_ID,
      UPDATE_OFFENDER_DATES.copy(
        keyDates = OffenderKeyDates(
          conditionalReleaseDate = THIRD_FEB_2021,
          sentenceExpiryDate = THIRD_FEB_2021,
          earlyRemovalSchemeEligibilityDate = FIFTH_APRIL_2021,
          effectiveSentenceEndDate = ESED_DATE,
          sentenceLength = "06/02/03",
        ),
        comment = "The NOMIS Reason",
        reason = "UPDATE",
      ),
    )
    verify(eventService).publishReleaseDatesChangedEvent(PRISONER_ID, BOOKING_ID)
  }

  @Test
  fun `Test that exception with correct message is thrown if write to NOMIS fails `() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA),
      ),
    )
    whenever(
      prisonService.postReleaseDates(any(), any()),
    ).thenThrow(EntityNotFoundException("test ex"))

    val exception = assertThrows<EntityNotFoundException> {
      calculationConfirmationService.writeToNomisAndPublishEvent(
        PRISONER_ID,
        BOOKING,
        BOOKING_CALCULATION,
        emptyList(),
      )
    }

    assertThat(exception)
      .isInstanceOf(EntityNotFoundException::class.java)
      .withFailMessage(
        "Writing release dates to NOMIS failed for prisonerId $PRISONER_ID " +
          "and bookingId $BOOKING_ID",
      )
  }

  @Test
  fun `Test that if the publishing of an event fails the exception is handled and not propagated`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA),
      ),
    )
    whenever(
      eventService.publishReleaseDatesChangedEvent(PRISONER_ID, BOOKING_ID),
    ).thenThrow(EntityNotFoundException("test ex"))

    try {
      calculationConfirmationService.writeToNomisAndPublishEvent(
        PRISONER_ID,
        BOOKING,
        BOOKING_CALCULATION,
        emptyList(),
      )
    } catch (ex: Exception) {
      fail("Exception was thrown!")
    }
  }

  @Test
  fun `Calculation with historic equal LED and SED should result in single SLED outcome`() {
    val requestAndOutcomes = CALCULATION_REQUEST_WITH_OUTCOMES.copy(
      calculationOutcomes = CALCULATION_REQUEST_WITH_OUTCOMES.calculationOutcomes.plus(
        CALCULATION_OUTCOME_SLED,
      ),
    )
    val calculationTransactionalService = calculationTransactionalService()
    val expectedSled = LocalDate.of(2026, 2, 2)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(any(), any()))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)))
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(
      fakeSourceData,
    )
    whenever(calculationRequestRepository.findById(any())).thenReturn(
      Optional.of(requestAndOutcomes),
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(requestAndOutcomes))
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(calculationTransactionalService.historicDatesFromSled(fakeSourceData.prisonerDetails.offenderNo, expectedSled)).thenReturn(
      listOf(
        CalculationOutcome(
          calculationRequestId = 1,
          outcomeDate = FIFTH_APRIL_2021,
          calculationDateType = LED.name,
        ),
        CalculationOutcome(
          calculationRequestId = 2,
          outcomeDate = FIFTH_APRIL_2021,
          calculationDateType = SED.name,
        ),
      ),
    )
    val sledOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = FIFTH_APRIL_2021,
      calculationDateType = SLED.name,
    )
    val crdOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2023, 8, 4),
      calculationDateType = CRD.name,
    )
    val hdcedOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2023, 2, 6),
      calculationDateType = HDCED.name,
    )
    val esedOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2026, 2, 2),
      calculationDateType = ESED.name,
    )
    whenever(calculationOutcomeRepository.save(crdOutcome)).thenReturn(crdOutcome)
    whenever(calculationOutcomeRepository.save(hdcedOutcome)).thenReturn(hdcedOutcome)
    whenever(calculationOutcomeRepository.save(esedOutcome)).thenReturn(esedOutcome)
    whenever(calculationOutcomeRepository.save(sledOutcome)).thenReturn(sledOutcome)
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    val submission = ApprovedDatesSubmission(
      calculationRequest = CALCULATION_REQUEST,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      submittedByUsername = USERNAME,
    )
    whenever(approvedDatesSubmissionRepository.save(any())).thenReturn(submission)
    val calculation = calculationTransactionalService.validateAndConfirmCalculation(
      CALCULATION_REQUEST_ID,
      SubmitCalculationRequest(
        calculationFragments = CalculationFragments(""),
        approvedDates = listOf(
          ManualEntrySelectedDate(ROTL, "rotl text", SubmittedDate(1, 1, 2020)),
          ManualEntrySelectedDate(APD, "apd text", SubmittedDate(1, 2, 2020)),
          ManualEntrySelectedDate(HDCAD, "hdcad text", SubmittedDate(1, 3, 2020)),
        ),
      ),
    )
    assertEquals(
      calculation.dates,
      mapOf(
        SLED to sledOutcome.outcomeDate,
        CRD to crdOutcome.outcomeDate,
        HDCED to hdcedOutcome.outcomeDate,
        ESED to esedOutcome.outcomeDate,
      ),
    )
  }

  @Test
  fun `Calculation with historic LED and SED should result in separate LED and SED outcome`() {
    val requestAndOutcomes = CALCULATION_REQUEST_WITH_OUTCOMES.copy(
      calculationOutcomes = CALCULATION_REQUEST_WITH_OUTCOMES.calculationOutcomes.plus(
        CALCULATION_OUTCOME_SLED,
      ),
    )
    val calculationTransactionalService = calculationTransactionalService()
    val expectedSled = LocalDate.of(2026, 2, 2)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(any(), any()))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)))
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(
      fakeSourceData,
    )
    whenever(calculationRequestRepository.findById(any())).thenReturn(
      Optional.of(requestAndOutcomes),
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(requestAndOutcomes))
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(calculationTransactionalService.historicDatesFromSled(fakeSourceData.prisonerDetails.offenderNo, expectedSled)).thenReturn(
      listOf(
        CalculationOutcome(
          calculationRequestId = 1,
          outcomeDate = FIFTH_APRIL_2021.plusYears(10),
          calculationDateType = LED.name,
        ),
        CalculationOutcome(
          calculationRequestId = 2,
          outcomeDate = FIFTH_APRIL_2021.plusYears(8),
          calculationDateType = SED.name,
        ),
      ),
    )
    val ledOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = FIFTH_APRIL_2021.plusYears(10),
      calculationDateType = LED.name,
    )
    val sedOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = FIFTH_APRIL_2021.plusYears(8),
      calculationDateType = SED.name,
    )
    val crdOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2023, 8, 4),
      calculationDateType = CRD.name,
    )
    val hdcedOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2023, 2, 6),
      calculationDateType = HDCED.name,
    )
    val esedOutcome = CalculationOutcome(
      id = -1,
      calculationRequestId = CALCULATION_REQUEST_ID,
      outcomeDate = LocalDate.of(2026, 2, 2),
      calculationDateType = ESED.name,
    )
    whenever(calculationOutcomeRepository.save(crdOutcome)).thenReturn(crdOutcome)
    whenever(calculationOutcomeRepository.save(hdcedOutcome)).thenReturn(hdcedOutcome)
    whenever(calculationOutcomeRepository.save(esedOutcome)).thenReturn(esedOutcome)
    whenever(calculationOutcomeRepository.save(ledOutcome)).thenReturn(ledOutcome)
    whenever(calculationOutcomeRepository.save(sedOutcome)).thenReturn(sedOutcome)
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    val submission = ApprovedDatesSubmission(
      calculationRequest = CALCULATION_REQUEST,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      submittedByUsername = USERNAME,
    )
    whenever(approvedDatesSubmissionRepository.save(any())).thenReturn(submission)
    val calculation = calculationTransactionalService.validateAndConfirmCalculation(
      CALCULATION_REQUEST_ID,
      SubmitCalculationRequest(
        calculationFragments = CalculationFragments(""),
        approvedDates = listOf(
          ManualEntrySelectedDate(ROTL, "rotl text", SubmittedDate(1, 1, 2020)),
          ManualEntrySelectedDate(APD, "apd text", SubmittedDate(1, 2, 2020)),
          ManualEntrySelectedDate(HDCAD, "hdcad text", SubmittedDate(1, 3, 2020)),
        ),
      ),
    )
    assertEquals(
      calculation.dates,
      mapOf(
        LED to ledOutcome.outcomeDate,
        SED to sedOutcome.outcomeDate,
        CRD to crdOutcome.outcomeDate,
        HDCED to hdcedOutcome.outcomeDate,
        ESED to esedOutcome.outcomeDate,
      ),
    )
  }

  @Test
  fun `Test that if approved dates are submitted then they get submitted to the database`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(CALCULATION_REQUEST_ID, PRELIMINARY.name))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)))
    whenever(calculationSourceDataService.getCalculationSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId, InactiveDataOptions.default())).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    val submission = ApprovedDatesSubmission(
      calculationRequest = CALCULATION_REQUEST,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      submittedByUsername = USERNAME,
    )
    whenever(approvedDatesSubmissionRepository.save(any())).thenReturn(submission)
    calculationTransactionalService().validateAndConfirmCalculation(
      CALCULATION_REQUEST_ID,
      SubmitCalculationRequest(
        calculationFragments = CalculationFragments(""),
        approvedDates = listOf(
          ManualEntrySelectedDate(ROTL, "rotl text", SubmittedDate(1, 1, 2020)),
          ManualEntrySelectedDate(APD, "apd text", SubmittedDate(1, 2, 2020)),
          ManualEntrySelectedDate(HDCAD, "hdcad text", SubmittedDate(1, 3, 2020)),
        ),
      ),
    )

    updatedOffenderDatesArgumentCaptor.apply {
      verify(prisonService).postReleaseDates(eq(BOOKING.bookingId), capture(this))
    }

    assertEquals("The NOMIS Reason", updatedOffenderDatesArgumentCaptor.value.comment)

    verify(approvedDatesSubmissionRepository).save(eq(submission))
  }

  @Test
  fun `Test that if dates are in approved dates they are used instead of the calculation`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA),
      ),
    )
    calculationConfirmationService.writeToNomisAndPublishEvent(
      PRISONER_ID,
      BOOKING.copy(sentences = listOf(StandardSENTENCE.copy(duration = ZERO_DURATION))),
      BOOKING_CALCULATION.copy(
        dates = mutableMapOf(
          CRD to CALCULATION_OUTCOME_CRD.outcomeDate!!,
          SED to THIRD_FEB_2021,
          ERSED to FIFTH_APRIL_2021,
          ESED to ESED_DATE,
        ),
        effectiveSentenceLength = Period.of(6, 2, 3),
      ),
      listOf(
        ManualEntrySelectedDate(ROTL, "rotl text", SubmittedDate(1, 1, 2020)),
        ManualEntrySelectedDate(APD, "apd text", SubmittedDate(1, 2, 2020)),
        ManualEntrySelectedDate(HDCAD, "hdcad text", SubmittedDate(1, 3, 2020)),
      ),
    )
    doNothing().`when`(prisonService).postReleaseDates(any(), any())
    updatedOffenderDatesArgumentCaptor.apply {
      verify(prisonService).postReleaseDates(eq(BOOKING.bookingId), capture(this))
    }
//    verify(prisonService).postReleaseDates(eq(BOOKING.bookingId), updatedOffenderDatesArgumentCaptor.capture())
    val submittedDates = updatedOffenderDatesArgumentCaptor.value.keyDates
    assertThat(submittedDates.releaseOnTemporaryLicenceDate).isEqualTo("2020-01-01")
    assertThat(submittedDates.approvedParoleDate).isEqualTo("2020-02-01")
    assertThat(submittedDates.homeDetentionCurfewApprovedDate).isEqualTo("2020-03-01")
  }

  @Test
  fun `Test that the specialist support comment is written to NOMIS`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(
      calculationRequestRepository.findById(
        CALCULATION_REQUEST_ID,
      ),
    ).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA),
      ),
    )
    whenever(nomisCommentService.getNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")

    calculationConfirmationService.writeToNomisAndPublishEvent(
      PRISONER_ID,
      BOOKING.copy(sentences = listOf(StandardSENTENCE.copy(duration = ZERO_DURATION))),
      BOOKING_CALCULATION.copy(
        dates = mutableMapOf(
          CRD to CALCULATION_OUTCOME_CRD.outcomeDate!!,
          SED to THIRD_FEB_2021,
          ERSED to FIFTH_APRIL_2021,
          ESED to ESED_DATE,
        ),
        effectiveSentenceLength = Period.of(6, 2, 3),
      ),
      emptyList(),
      true,
    )

    verify(prisonService).postReleaseDates(
      BOOKING_ID,
      UPDATE_OFFENDER_DATES.copy(
        keyDates = OffenderKeyDates(
          conditionalReleaseDate = THIRD_FEB_2021,
          sentenceExpiryDate = THIRD_FEB_2021,
          earlyRemovalSchemeEligibilityDate = FIFTH_APRIL_2021,
          effectiveSentenceEndDate = ESED_DATE,
          sentenceLength = "06/02/03",
        ),
        comment = "The NOMIS Reason",
        reason = "UPDATE",
      ),
    )
    verify(eventService).publishReleaseDatesChangedEvent(PRISONER_ID, BOOKING_ID)
  }

  @Test
  fun `If the NOMIS data has changed and checkForChange is true, throw exception`() {
    whenever(calculationRequestRepository.findByCalculationReference(any())).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(anyString(), eq(InactiveDataOptions.default()), eq(emptyList()))).thenReturn(SOURCE_DATA)
    whenever(bookingService.getBooking(eq(SOURCE_DATA), any())).thenReturn(BOOKING)
    assertThrows<CalculationDataHasChangedError> {
      calculationTransactionalService().findCalculationResultsByCalculationReference(
        UUID.randomUUID().toString(),
        true,
      )
    }
  }

  private fun <T> capture(argumentCaptor: ArgumentCaptor<T>): T = argumentCaptor.capture()

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(cachedBankHolidays)
  }

  private fun calculationTransactionalService(
    params: String = "calculation-params",
    passedInServices: List<String> = emptyList(),
  ): CalculationTransactionalService = setupServices(params, passedInServices).first

  private fun calculationService(
    params: String = "calculation-params",
    passedInServices: List<String> = emptyList(),
  ): CalculationService = setupServices(params, passedInServices).second

  private fun setupServices(
    params: String = "calculation-params",
    passedInServices: List<String> = emptyList(),
  ): Pair<CalculationTransactionalService, CalculationService> {
    val hdcedConfiguration =
      hdcedConfigurationForTests() // HDCED and ERSED params not currently overridden in alt-calculation-params

    val ersedConfiguration = ersedConfigurationForTests()
    val releasePointMultipliersConfiguration = releasePointMultiplierConfigurationForTests(params)

    val workingDayService = WorkingDayService(bankHolidayService)
    val tusedCalculator = TusedCalculator(workingDayService)

    val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
    val ersedCalculator = ErsedCalculator(ersedConfiguration)
    val sentenceAdjustedCalculationService =
      SentenceAdjustedCalculationService(tusedCalculator, hdcedCalculator, ersedCalculator)
    val sentencesExtractionService = SentencesExtractionService()
    val trancheConfiguration = SDS40TrancheConfiguration(
      sdsEarlyReleaseTrancheOneDate(params),
      sdsEarlyReleaseTrancheTwoDate(params),
      sdsEarlyReleaseTrancheThreeDate(params),
    )

    val sentenceIdentificationService = SentenceIdentificationService(tusedCalculator, hdcedCalculator, trancheConfiguration)

    val tranche = Tranche(trancheConfiguration)

    val trancheAllocationService = TrancheAllocationService(tranche, trancheConfiguration)
    val sdsEarlyReleaseDefaultingRulesService = SDSEarlyReleaseDefaultingRulesService(trancheConfiguration)
    val sentenceCombinationService = SentenceCombinationService(
      sentenceIdentificationService,
    )

    val hdcedExtractionService = HdcedExtractionService(
      sentencesExtractionService,
    )
    val bookingExtractionService = BookingExtractionService(
      hdcedExtractionService,
      sentencesExtractionService,
      fixedTermRecallsService,
    )
    val timelineCalculator = TimelineCalculator(
      sentenceAdjustedCalculationService,
      bookingExtractionService,
    )
    val timelineAwardedAdjustmentCalculationHandler = TimelineAwardedAdjustmentCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
    )
    val timelineSentenceCalculationHandler = TimelineSentenceCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
      sentenceCombinationService,
    )
    val timelineTrancheCalculationHandler = TimelineTrancheCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
      trancheAllocationService,
      sentencesExtractionService,
    )
    val timelineTrancheThreeCalculationHandler = TimelineTrancheThreeCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
    )
    val timelineUalAdjustmentCalculationHandler = TimelineUalAdjustmentCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
    )
    val timelineExternalReleaseMovementCalculationHandler = TimelineExternalReleaseMovementCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
      FeatureToggles(externalMovementsSds40 = true, externalMovementsAdjustmentSharing = true),
    )
    val timelineExternalAdmissionMovementCalculationHandler = TimelineExternalAdmissionMovementCalculationHandler(
      trancheConfiguration,
      releasePointMultipliersConfiguration,
      timelineCalculator,
    )
    val timelinePostTrancheAdjustmentService = TimelinePostTrancheAdjustmentService()

    val bookingTimelineService = BookingTimelineService(
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
      timelinePostTrancheAdjustmentService,
    )

    val sourceDataMapper = SourceDataMapper(TestUtil.objectMapper())

    val calculationService = CalculationService(
      sentenceIdentificationService,
      bookingTimelineService,
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
      calculationOutcomeHistoricOverrideRepository,
      TestUtil.objectMapper(),
      calculationSourceDataService,
      sourceDataMapper,
      calculationService,
      bookingService,
      validationServiceToUse,
      serviceUserService,
      calculationConfirmationService,
      dominantHistoricDateService,
      TEST_BUILD_PROPERTIES,
      trancheOutcomeRepository,
      FeatureToggles(historicSled = true),
    ) to calculationService
  }

  fun parseFeatureToggles(toggleStr: String?): FeatureToggles {
    val defaults = FeatureToggles(ftr48ManualJourney = true)
    if (toggleStr.isNullOrBlank()) return defaults

    val toggleMap = toggleStr.split(';').associate {
      val (key, value) = it.split('=').map(String::trim)
      key to value.toBooleanStrict()
    }

    return FeatureToggles(
      supportInactiveSentencesAndAdjustments = toggleMap[FeatureToggles::supportInactiveSentencesAndAdjustments.name] ?: defaults.supportInactiveSentencesAndAdjustments,
      useAdjustmentsApi = toggleMap[FeatureToggles::useAdjustmentsApi.name] ?: defaults.useAdjustmentsApi,
      concurrentConsecutiveSentencesEnabled = toggleMap[FeatureToggles::concurrentConsecutiveSentencesEnabled.name] ?: defaults.concurrentConsecutiveSentencesEnabled,
      externalMovementsSds40 = toggleMap[FeatureToggles::externalMovementsSds40.name] ?: defaults.externalMovementsSds40,
      externalMovementsAdjustmentSharing = toggleMap[FeatureToggles::externalMovementsAdjustmentSharing.name] ?: defaults.externalMovementsAdjustmentSharing,
      historicSled = toggleMap[FeatureToggles::historicSled.name] ?: defaults.historicSled,
      ftr48ManualJourney = toggleMap[FeatureToggles::ftr48ManualJourney.name] ?: defaults.ftr48ManualJourney,
    )
  }

  private fun getActiveValidationService(
    sentencesExtractionService: SentencesExtractionService,
    trancheConfiguration: SDS40TrancheConfiguration,
    featureToggles: FeatureToggles = FeatureToggles(),
  ): ValidationService {
    val validationUtilities = ValidationUtilities()
    val fineValidationService = FineValidationService(validationUtilities)
    val adjustmentValidationService = AdjustmentValidationService()
    val dtoValidationService = DtoValidationService()
    val botusValidationService = BotusValidationService(featureToggles)
    val recallValidationService = RecallValidationService(trancheConfiguration, validationUtilities, featureToggles)
    val unsupportedValidationService = UnsupportedValidationService()
    val postCalculationValidationService = PostCalculationValidationService(trancheConfiguration)
    val section91ValidationService = Section91ValidationService(validationUtilities)
    val sopcValidationService = SOPCValidationService(validationUtilities)
    val edsValidationService = EDSValidationService(validationUtilities)
    val toreraValidationService = ToreraValidationService(manageOffencesService)
    val dateValidationService = DateValidationService()
    val sentenceValidationService = SentenceValidationService(
      validationUtilities,
      sentencesExtractionService,
      section91ValidationService = section91ValidationService,
      sopcValidationService = sopcValidationService,
      fineValidationService,
      edsValidationService = edsValidationService,
      featuresToggles = featureToggles,
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
      dateValidationService = dateValidationService,
    )
  }

  companion object {
    @JvmStatic
    fun testCaseSource(): Stream<Arguments> {
      val excluded = listOf("custom-examples/different-calclulation-from-stored")
      val dir = File(object {}.javaClass.getResource("/test_data/overall_calculation").file)
      return getTestCasesFromDir(dir, excluded)
    }

    @JvmStatic
    fun breakdownTestCaseSource(): Stream<Arguments> {
      val dir = File(object {}.javaClass.getResource("/test_data/calculation_breakdown_response").file)
      return getTestCasesFromDir(dir, listOf())
    }

    private fun getTestCasesFromDir(dir: File, excluded: List<String>): Stream<Arguments> {
      val args = mutableListOf<String>()
      JsonTransformation().doAllInDir(
        dir,
      ) {
        val arg = it.path.replace(dir.path + "/", "").replace(".json", "")
        if (!excluded.contains(arg)) {
          args.add(arg)
        }
      }
      return args.stream().map { Arguments.of(it) }
    }

    private val originalSentence = PrisonApiSentenceAndOffences(
      bookingId = 1L,
      sentenceSequence = 3,
      lineSequence = 2,
      caseSequence = 1,
      sentenceDate = ImportantDates.PCSC_COMMENCEMENT_DATE.minusDays(1),
      terms = listOf(
        SentenceTerms(years = 8),
      ),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      offences = listOf(OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A"))),
    )
    private val prisonerDetails = PrisonerDetails(
      1,
      "asd",
      dateOfBirth = LocalDate.of(1980, 1, 1),
      firstName = "Zimmy",
      lastName = "Cnys",
    )
    private val adjustments = BookingAndSentenceAdjustments(
      bookingAdjustments = emptyList(),
      sentenceAdjustments = listOf(
        SentenceAdjustment(
          sentenceSequence = 1,
          active = true,
          fromDate = LocalDate.of(2021, 1, 30),
          toDate = LocalDate.of(2021, 1, 31),
          numberOfDays = 1,
          type = SentenceAdjustmentType.REMAND,
        ),
      ),
    )
    val SOURCE_DATA =
      CalculationSourceData(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            originalSentence,
            originalSentence.offences[0],
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
        prisonerDetails,
        AdjustmentsSourceData(prisonApiData = adjustments),
        emptyList(),
        null,
        null,
      )
    val cachedBankHolidays =
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
    const val USERNAME = "user1"
    private const val PRISONER_ID = "A1234AJ"
    private const val BOOKING_ID = 12345L
    private const val CALCULATION_REQUEST_ID = 123456L
    private val ZERO_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 0L))
    private val FIVE_YEAR_DURATION = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L))
    val ESED_DATE: LocalDate = LocalDate.of(2021, 5, 5)
    private val CALCULATION_REFERENCE: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
    private val THIRD_FEB_2021 = LocalDate.of(2021, 2, 3)
    private val FIFTH_APRIL_2021 = LocalDate.of(2021, 4, 5)
    private val CALCULATION_OUTCOME_CRD = CalculationOutcome(
      calculationDateType = CRD.name,
      outcomeDate = THIRD_FEB_2021,
      calculationRequestId = CALCULATION_REQUEST_ID,
    )
    private val CALCULATION_OUTCOME_SED = CalculationOutcome(
      calculationDateType = SED.name,
      outcomeDate = THIRD_FEB_2021,
      calculationRequestId = CALCULATION_REQUEST_ID,
    )
    private val CALCULATION_OUTCOME_SLED = CalculationOutcome(
      calculationDateType = SLED.name,
      outcomeDate = THIRD_FEB_2021,
      calculationRequestId = CALCULATION_REQUEST_ID,
    )
    val CALCULATION_REQUEST = CalculationRequest(
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
    )
    val INPUT_DATA: JsonNode =
      JacksonUtil.toJsonNode(
        "{\"externalMovements\":[], \"historicalTusedData\":null, \"offender\":{\"reference\":\"A1234AJ\",\"dateOfBirth\":\"1980-01-01\",\"isActiveSexOffender\":false}," +
          "\"sentences\":[{\"type\":\"StandardSentence\",\"offence\":{\"committedAt\":\"2021-02-03\"," +
          "\"offenceCode\":null},\"duration\":{\"durationElements\":{\"DAYS\":0,\"WEEKS\":0,\"MONTHS\":0,\"YEARS\":5}}," +
          "\"sentencedAt\":\"2021-02-03\",\"identifier\":\"5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa\"," +
          "\"consecutiveSentenceUUIDs\":[],\"caseSequence\":1,\"lineSequence\":2,\"externalSentenceId\":{\"sentenceSequence\":1,\"bookingId\":12345},\"caseReference\":null," +
          "\"recallType\":null,\"isSDSPlus\":false,\"isSDSPlusEligibleSentenceTypeLengthAndOffence\":false,\"isSDSPlusOffenceInPeriod\":false,\"hasAnSDSEarlyReleaseExclusion\":\"NO\"}],\"adjustments\":{},\"returnToCustodyDate\":null,\"fixedTermRecallDetails\":null," +
          "\"bookingId\":12345}",
      )

    val CALCULATION_REASON =
      CalculationReason(-1, true, false, "Reason", false, nomisReason = "UPDATE", nomisComment = "NOMIS_COMMENT", null)

    val CALCULATION_REQUEST_WITH_OUTCOMES = CalculationRequest(
      id = CALCULATION_REQUEST_ID,
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationOutcomes = listOf(CALCULATION_OUTCOME_CRD, CALCULATION_OUTCOME_SED),
      calculationStatus = CONFIRMED.name,
      inputData = JacksonUtil.toJsonNode(
        "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
          "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
          "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
          "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
          "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}",
      ),
      reasonForCalculation = CALCULATION_REASON,
    )

    val BOOKING_CALCULATION = CalculatedReleaseDates(
      dates = mutableMapOf(CRD to CALCULATION_OUTCOME_CRD.outcomeDate!!, SED to THIRD_FEB_2021),
      calculationRequestId = CALCULATION_REQUEST_ID,
      bookingId = BOOKING_ID,
      prisonerId = PRISONER_ID,
      calculationStatus = CONFIRMED,
      calculationReference = CALCULATION_REFERENCE,
      calculationReason = CALCULATION_REASON,
      calculationDate = LocalDate.now(),
    )

    val UPDATE_OFFENDER_DATES = UpdateOffenderDates(
      calculationUuid = CALCULATION_REQUEST_WITH_OUTCOMES.calculationReference,
      submissionUser = USERNAME,
      keyDates = OffenderKeyDates(
        conditionalReleaseDate = THIRD_FEB_2021,
        licenceExpiryDate = THIRD_FEB_2021,
        sentenceExpiryDate = THIRD_FEB_2021,
        effectiveSentenceEndDate = THIRD_FEB_2021,
        sentenceLength = "11/00/00",
      ),
      noDates = false,
    )

    private val OFFENDER = Offender(PRISONER_ID, LocalDate.of(1980, 1, 1))

    private val StandardSENTENCE = StandardDeterminateSentence(
      sentencedAt = THIRD_FEB_2021,
      duration = FIVE_YEAR_DURATION,
      offence = Offence(committedAt = THIRD_FEB_2021),
      identifier = UUID.fromString("5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa"),
      caseSequence = 1,
      lineSequence = 2,
      externalSentenceId = ExternalSentenceId(sentenceSequence = 1, bookingId = BOOKING_ID),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )

    val BOOKING = Booking(OFFENDER, listOf(StandardSENTENCE), Adjustments(), null, null, BOOKING_ID)
  }
}

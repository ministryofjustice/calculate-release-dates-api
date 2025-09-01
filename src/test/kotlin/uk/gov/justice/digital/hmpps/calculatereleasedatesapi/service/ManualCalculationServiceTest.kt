package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.doNothing
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.integration.TestBuildPropertiesConfiguration.Companion.TEST_BUILD_PROPERTIES
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ConsecutiveSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SupportedValidationResponse
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceCombinationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*

class ManualCalculationServiceTest {
  private val prisonService = mock<PrisonService>()
  private val calculationSourceDataService = mock<CalculationSourceDataService>()
  private val bookingService = mock<BookingService>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val objectMapper = TestUtil.objectMapper()
  private val eventService = mock<EventService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val sentenceIdentificationService = mock<SentenceIdentificationService>()
  private val sentenceCombinationService = mock<SentenceCombinationService>()
  private val validationService = mock<ValidationService>()
  private val calculationService = mock<CalculationService>()
  private val manualCalculationService = ManualCalculationService(
    prisonService,
    bookingService,
    calculationOutcomeRepository,
    calculationRequestRepository,
    calculationReasonRepository,
    objectMapper,
    eventService,
    serviceUserService,
    nomisCommentService,
    TEST_BUILD_PROPERTIES,
    sentenceIdentificationService,
    sentenceCombinationService,
    validationService,
    calculationSourceDataService,
    calculationService,
  )
  private val calculationRequestArgumentCaptor = argumentCaptor<CalculationRequest>()

  @Nested
  inner class CalculateESLTests {
    @Test
    fun `Check ESL is calculated correctly for determinate sentences without SED`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.NP.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(
        BOOKING,
        ManualEntryRequest(
          listOf(
            ManualEntrySelectedDate(
              ReleaseDateType.LED,
              "None",
              SubmittedDate(1, 1, 2026),
            ),
          ),
          1L,
          "",
        ),
      )

      // Assert
      assertThat(result).isEqualTo(Period.of(0, 0, 0))
    }

    @Test
    fun `Check ESL is set to zero for FTR 14 without custody date`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
        recallType = RecallType.FIXED_TERM_RECALL_14,
      )
      val workingBooking = BOOKING.copy(
        returnToCustodyDate = null,
        sentences = listOf(
          sentenceOne,
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(workingBooking, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(0, 0, 0))
    }

    @Test
    fun `Check if ESL is set to zero for when calculated ESL is negative`() {
      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(0, 0, 0))
    }

    @Test
    fun `Check ESL is set to zero for FTR 28 without custody date`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
        recallType = RecallType.FIXED_TERM_RECALL_28,
      )
      val workingBooking = BOOKING.copy(
        returnToCustodyDate = null,
        sentences = listOf(
          sentenceOne,
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(workingBooking, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(0, 0, 0))
    }

    @Test
    fun `Check ESL is calculated correctly for indeterminate sentences`() {
      // Arrange
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE,
            false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(0, 0, 0))
    }

    @Test
    fun `Check ESL is calculated correctly (2 concurrent) for determinate sentences`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
      )
      val sentenceTwo = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2021, 1, 1),
      )

      whenever(sentenceCombinationService.getSentencesToCalculate(any(), any())).thenReturn(
        listOf(
          sentenceOne,
          sentenceTwo,
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(5, 0, 0))
    }

    @Test
    fun `Check ESL is calculated correctly (single) for determinate sentences`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
      )

      whenever(sentenceCombinationService.getSentencesToCalculate(any(), any())).thenReturn(
        listOf(
          sentenceOne,
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(4, 0, 0))
    }

    @Test
    fun `Check ESL is calculated correctly (consecutive) for determinate sentences`() {
      // Arrange
      val consecutiveSentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
      )
      val consecutiveSentenceTwo = StandardSENTENCE.copy(
        identifier = UUID.randomUUID(),
        sentencedAt = LocalDate.of(2023, 1, 1),
        consecutiveSentenceUUIDs = listOf(StandardSENTENCE.identifier),
      )

      whenever(sentenceCombinationService.getSentencesToCalculate(any(), any())).thenReturn(
        listOf(
          ConsecutiveSentence(
            listOf(
              consecutiveSentenceOne,
              consecutiveSentenceTwo,
            ),
          ),
        ),
      )

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(4, 10, 29))
    }
  }

  @Nested
  inner class RecallSentencesTests {
    @Test
    fun `Check the presence of recall sentences returns true`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.LR_ORA.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      val hasRecallSentences = manualCalculationService.hasRecallSentences(BOOKING_ID)

      assertThat(hasRecallSentences).isTrue()
    }

    @Test
    fun `Check the absence of licenceRecall sentences returns false`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.ADIMP_ORA.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            source = BASE_DETERMINATE_SENTENCE,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      val hasRecallSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasRecallSentences).isFalse()
    }
  }

  @Nested
  inner class IndeterminateSentencesTests {
    @Test
    fun `Check the presence of indeterminate sentences returns true`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            source = BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            source = BASE_DETERMINATE_SENTENCE,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isTrue()
    }

    @Test
    fun `Check the absence of indeterminate sentences returns false`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR.name),
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
          SentenceAndOffenceWithReleaseArrangements(
            BASE_DETERMINATE_SENTENCE,
            isSdsPlus = false,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
            isSDSPlusOffenceInPeriod = false,
            hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
          ),
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isFalse()
    }
  }

  @Test
  fun `Check noDates is set correctly when indeterminate`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any())).thenReturn(
      SupportedValidationResponse(
        listOf(),
        listOf(),
      ),
    )
    val manualCalcRequest = ManualEntrySelectedDate(ReleaseDateType.None, "None of the dates apply", null)
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")
    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest)
    val expectedUpdatedOffenderDates = UpdateOffenderDates(
      calculationUuid = CALCULATION_REFERENCE,
      submissionUser = USERNAME,
      keyDates = OffenderKeyDates(),
      noDates = true,
      comment = "The NOMIS Reason",
      reason = "UPDATE",
    )
    verify(prisonService).postReleaseDates(BOOKING_ID, expectedUpdatedOffenderDates)
    verify(eventService).publishReleaseDatesChangedEvent(PRISONER_ID, BOOKING_ID)
  }

  @Test
  fun `Check noDates is set correctly for determinate manual entry`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any())).thenReturn(
      SupportedValidationResponse(
        listOf(),
        listOf(),
      ),
    )

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest)
    val expectedUpdatedOffenderDates = UpdateOffenderDates(
      calculationUuid = CALCULATION_REFERENCE,
      submissionUser = USERNAME,
      keyDates = OffenderKeyDates(conditionalReleaseDate = LocalDate.of(2023, 3, 3)),
      noDates = false,
      comment = "The NOMIS Reason",
      reason = "UPDATE",
    )
    verify(prisonService).postReleaseDates(BOOKING_ID, expectedUpdatedOffenderDates)
    verify(eventService).publishReleaseDatesChangedEvent(PRISONER_ID, BOOKING_ID)
  }

  @Test
  fun `Check type is set to Genuine Override when its a genuine override`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any()))
      .thenReturn(SupportedValidationResponse())

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, true)
    verify(calculationRequestRepository, times(2)).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_OVERRIDE)
  }

  @Test
  fun `Check type is set to manual indeterminate when indeterminate sentences are present`() {
    val offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP", "description", listOf("A"))
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any()))
      .thenReturn(SupportedValidationResponse())
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(
      listOf(
        SentenceAndOffenceWithReleaseArrangements(
          source = PrisonApiSentenceAndOffences(
            bookingId = 1,
            sentenceSequence = 1,
            lineSequence = 1,
            caseSequence = 1,
            consecutiveToSequence = null,
            sentenceStatus = "A",
            sentenceCategory = "A",
            sentenceCalculationType = "LIFE",
            sentenceTypeDescription = "",
            sentenceDate = LocalDate.now(),
            offences = listOf(offence),
          ),
          offence = offence,
          isSdsPlus = false,
          isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
          isSDSPlusOffenceInPeriod = false,
          hasAnSDSExclusion = SDSEarlyReleaseExclusionType.NO,
        ),
      ),
    )

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)
    verify(calculationRequestRepository, times(2)).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_INDETERMINATE)
  }

  @Test
  fun `Check ESL is set to zero when exception is thrown calculating ESL`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)

    // allow initial persist (and potential subsequent update) to succeed
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any()))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))

    whenever(calculationReasonRepository.findById(any()))
      .thenReturn(Optional.of(CALCULATION_REASON))

    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs()))
      .thenReturn(BOOKING)

    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    whenever(nomisCommentService.getNomisComment(any(), any(), any()))
      .thenReturn("The NOMIS Reason")

    whenever(validationService.validateSupportedSentencesAndCalculations(any()))
      .thenReturn(SupportedValidationResponse())

    whenever(sentenceCombinationService.createConsecutiveSentences(any(), any()))
      .thenThrow(NullPointerException("An error was thrown"))

    whenever(calculationService.calculateReleaseDates(any(), any())).thenReturn(
      CalculationOutput(
        emptyList(),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(9, 9, 9), // this value should be ignored because ESL was zeroed earlier
          dates = mapOf(
            ReleaseDateType.CRD to LocalDate.of(2023, 3, 3),
          ),
        ),
      ),
    )

    whenever(validationService.validateBookingAfterCalculation(any(), any()))
      .thenReturn(emptyList())

    doNothing().`when`(prisonService).postReleaseDates(any(), any())

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)

    val updatedDatesCapture = argumentCaptor<UpdateOffenderDates>()

    verify(calculationRequestRepository, atLeastOnce()).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_DETERMINATE)

    verify(prisonService).postReleaseDates(anyLong(), updatedDatesCapture.capture())
    val updateOffenderDates = updatedDatesCapture.firstValue

    assertThat(updateOffenderDates.keyDates.sentenceLength).isEqualTo("00/00/00")
  }

  @Test
  fun `Check type dates for bookings with fines can be manually set`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any()))
      .thenReturn(SupportedValidationResponse())
    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)
    verify(calculationRequestRepository, times(2)).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_DETERMINATE)
  }

  @Test
  fun `Check manual journey records validation reasons`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(validationService.validateSupportedSentencesAndCalculations(any())).thenReturn(
      SupportedValidationResponse(
        unsupportedSentenceMessages = listOf(),
        unsupportedCalculationMessages = listOf(
          ValidationMessage(ValidationCode.UNSUPPORTED_CALCULATION_DTO_WITH_RECALL),
          ValidationMessage(ValidationCode.BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE),
        ),
      ),
    )

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)

    // save should be called twice, one for initial creation, then one for manual calc reasons
    verify(calculationRequestRepository, times(2)).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_DETERMINATE)
  }

  @Test
  fun `Check manual journey records validation reasons for post calculation errors`() {
    whenever(
      calculationSourceDataService.getCalculationSourceData(
        PRISONER_ID,
        InactiveDataOptions.default(),
      ),
    ).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(calculationService.calculateReleaseDates(any(), any())).thenReturn(
      CalculationOutput(
        listOf(),
        listOf(),
        mock(),
      ),
    )
    whenever(validationService.validateSupportedSentencesAndCalculations(any())).thenReturn(
      SupportedValidationResponse(
        listOf(),
        listOf(),
      ),
    )
    whenever(validationService.validateBookingAfterCalculation(any(), any())).thenReturn(
      listOf(
        ValidationMessage(ValidationCode.UNSUPPORTED_SDS40_RECALL_SENTENCE_TYPE),
        ValidationMessage(ValidationCode.UNABLE_TO_DETERMINE_SHPO_RELEASE_PROVISIONS),
      ),
    )

    val manualCalcRequest = ManualEntrySelectedDate(
      ReleaseDateType.CRD,
      "CRD also known as the Conditional Release Date",
      SubmittedDate(3, 3, 2023),
    )
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)

    // save should be called twice, one for initial creation, then one for manual calc reasons
    verify(calculationRequestRepository, times(2)).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_DETERMINATE)
  }

  private companion object {
    private const val BOOKING_ID = 12345L
    private val THIRD_FEB_2021 = LocalDate.of(2021, 2, 3)
    private val FIVE_YEAR_DURATION = Duration(
      mutableMapOf(
        ChronoUnit.DAYS to 0L,
        ChronoUnit.WEEKS to 0L,
        ChronoUnit.MONTHS to 0L,
        ChronoUnit.YEARS to 5L,
      ),
    )
    private const val PRISONER_ID = "A1234AJ"

    private val BASE_DETERMINATE_SENTENCE = NormalisedSentenceAndOffence(
      bookingId = BOOKING_ID,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2022, 1, 1),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
      terms = emptyList(),
      offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP_ORA", "description", listOf("A")),
      caseReference = null,
      fineAmount = null,
      courtDescription = null,
      consecutiveToSequence = null,
    )
    private val FAKE_SOURCE_DATA = CalculationSourceData(
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
    private val CALCULATION_REFERENCE: UUID = UUID.fromString("219db65e-d7b7-4c70-9239-98babff7bcd5")
    private const val CALCULATION_REQUEST_ID = 123456L

    val CALCULATION_REASON = CalculationReason(
      id = 1,
      isActive = true,
      isOther = false,
      isBulk = false,
      displayName = "Reason",
      nomisReason = "UPDATE",
      nomisComment = "NOMIS_COMMENT",
      displayRank = null,
    )

    val CALCULATION_REQUEST_WITH_OUTCOMES = CalculationRequest(
      id = CALCULATION_REQUEST_ID,
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
      calculationOutcomes = listOf(),
      calculationStatus = CalculationStatus.CONFIRMED.name,
      inputData = JacksonUtil.toJsonNode(
        "{" + "\"offender\":{" + "\"reference\":\"ABC123D\"," +
          "\"dateOfBirth\":\"1970-03-03\"" + "}," + "\"sentences\":[" +
          "{" + "\"caseSequence\":1," + "\"lineSequence\":2," +
          "\"offence\":{" + "\"committedAt\":\"2013-09-19\"" + "}," + "\"duration\":{" +
          "\"durationElements\":{" + "\"YEARS\":2" + "}" + "}," + "\"sentencedAt\":\"2013-09-21\"" + "}" + "]" + "}",
      ),
      reasonForCalculation = CALCULATION_REASON,
    )
    private val OFFENDER = Offender(PRISONER_ID, LocalDate.of(1980, 1, 1))

    private val StandardSENTENCE = StandardDeterminateSentence(
      sentencedAt = THIRD_FEB_2021,
      duration = FIVE_YEAR_DURATION,
      offence = Offence(committedAt = THIRD_FEB_2021),
      identifier = UUID.fromString("5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa"),
      caseSequence = 1,
      lineSequence = 2,
      isSDSPlus = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    private val BOOKING = Booking(OFFENDER, listOf(StandardSENTENCE), Adjustments(), null, null, BOOKING_ID)
    private val MANUAL_ENTRY = ManualEntryRequest(
      listOf(
        ManualEntrySelectedDate(
          ReleaseDateType.SED,
          "None",
          SubmittedDate(1, 1, 2026),
        ),
      ),
      1L,
      "",
    )
    const val USERNAME = "user1"
  }
}

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
import org.junit.jupiter.params.provider.CsvFileSource
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.ApprovedDates
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ROTL
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.CalculationDataHasChangedError
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmitCalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.ApprovedDatesSubmissionRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalculationTransactionalServiceTest {
  private val jsonTransformation = JsonTransformation()
  private val hdcedConfiguration = HdcedCalculator.HdcedConfiguration(12, WEEKS, 4, YEARS, 14, 720, DAYS, 179)
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val hdced4configuration = Hdced4Calculator.Hdced4Configuration(12, WEEKS, 14, 720, DAYS, 179)
  private val hdced4Calculator = Hdced4Calculator(hdced4configuration)
  private val ersedConfiguration = ErsedCalculator.ErsedConfiguration(2180, 1635, 544)
  private val ersedCalculator = ErsedCalculator(ersedConfiguration)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(hdcedCalculator, tusedCalculator, hdced4Calculator, ersedCalculator)
  private val sentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService)
  private val sentencesExtractionService = SentencesExtractionService()
  private val sentenceIdentificationService =
    SentenceIdentificationService(hdcedCalculator, tusedCalculator, hdced4Calculator)
  private val bookingCalculationService = BookingCalculationService(
    sentenceCalculationService,
    sentenceIdentificationService,
  )
  private val bookingExtractionService = BookingExtractionService(
    sentencesExtractionService,
  )
  private val bookingTimelineService = BookingTimelineService(
    sentenceAdjustedCalculationService,
    sentencesExtractionService,
    workingDayService,
  )
  private val prisonApiDataMapper = PrisonApiDataMapper(TestUtil.objectMapper())

  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val prisonService = mock<PrisonService>()
  private val eventService = mock<EventService>()
  private val calculationService = CalculationService(
    bookingCalculationService,
    bookingExtractionService,
    bookingTimelineService,
  )
  private val bookingService = mock<BookingService>()
  private val validationService = mock<ValidationService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val approvedDatesSubmissionRepository = mock<ApprovedDatesSubmissionRepository>()
  private val nomisCommentService = mock<NomisCommentService>()

  private val calculationTransactionalService =
    CalculationTransactionalService(
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
    )

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

  @Captor
  lateinit var updatedOffenderDatesArgumentCaptor: ArgumentCaptor<UpdateOffenderDates>

  @Captor
  lateinit var approvedDatesArgumentCaptor: ArgumentCaptor<List<ApprovedDates>>

  @Test
  fun blah() {
    `Test Example`("custom-examples", "crs-1828-hdced-with-long-concurrent-license", null)
  }
  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/calculation-service-examples.csv"], numLinesToSkip = 1)
  fun `Test Example`(exampleType: String, exampleNumber: String, error: String?) {
    log.info("Testing example $exampleType/$exampleNumber")
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    val booking = jsonTransformation.loadBooking("$exampleType/$exampleNumber")
    val calculatedReleaseDates: CalculatedReleaseDates

    try {
      calculatedReleaseDates =
        calculationTransactionalService.calculate(booking, PRELIMINARY, fakeSourceData, CALCULATION_REASON, null)
    } catch (e: Exception) {
      if (!error.isNullOrEmpty()) {
        assertEquals(error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    log.info(
      "Example $exampleType/$exampleNumber outcome BookingCalculation: {}",
      TestUtil.objectMapper().writeValueAsString(calculatedReleaseDates),
    )
    val bookingData = jsonTransformation.loadCalculationResult("$exampleType/$exampleNumber")

    assertEquals(bookingData.dates, calculatedReleaseDates.dates)
    assertEquals(bookingData.effectiveSentenceLength, calculatedReleaseDates.effectiveSentenceLength)
  }

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/calculation-breakdown-examples.csv"], numLinesToSkip = 1)
  fun `Test UX Example Breakdowns`(exampleType: String, exampleNumber: String, error: String?) {
    log.info("Testing example $exampleType/$exampleNumber")
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    val booking = jsonTransformation.loadBooking("$exampleType/$exampleNumber")
    val calculation = jsonTransformation.loadCalculationResult("$exampleType/$exampleNumber")

    val calculationBreakdown: CalculationBreakdown?
    try {
      calculationBreakdown = calculationTransactionalService.calculateWithBreakdown(
        booking,
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
      )
    } catch (e: Exception) {
      if (!error.isNullOrEmpty()) {
        assertEquals(error, e.javaClass.simpleName)
        return
      } else {
        throw e
      }
    }
    log.info(
      "Example $exampleType/$exampleNumber outcome CalculationBreakdown: {}",
      TestUtil.objectMapper().writeValueAsString(calculationBreakdown),
    )
    assertEquals(
      jsonTransformation.loadCalculationBreakdown("$exampleType/$exampleNumber"),
      calculationBreakdown,
    )
  }

  @Test
  fun `Test fetching calculation results by requestId`() {
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(
      Optional.of(
        CALCULATION_REQUEST_WITH_OUTCOMES,
      ),
    )

    val bookingCalculation = calculationTransactionalService.findCalculationResults(CALCULATION_REQUEST_ID)

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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<PreconditionFailedException> {
      calculationTransactionalService.validateAndConfirmCalculation(
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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(
      fakeSourceData,
    )
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<EntityNotFoundException> {
      calculationTransactionalService.validateAndConfirmCalculation(
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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(
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
      calculationTransactionalService.validateAndConfirmCalculation(
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

    calculationTransactionalService.writeToNomisAndPublishEvent(
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
      calculationTransactionalService.writeToNomisAndPublishEvent(
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
      calculationTransactionalService.writeToNomisAndPublishEvent(
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
  fun `Test that if approved dates are submitted then they get submitted to the database`() {
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(calculationRequestRepository.findByIdAndCalculationStatus(CALCULATION_REQUEST_ID, PRELIMINARY.name))
      .thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES.copy(inputData = INPUT_DATA)))
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(
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
    calculationTransactionalService.validateAndConfirmCalculation(
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
    calculationTransactionalService.writeToNomisAndPublishEvent(
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

    calculationTransactionalService.writeToNomisAndPublishEvent(
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
    whenever(prisonService.getPrisonApiSourceData(anyString(), eq(true))).thenReturn(SOURCE_DATA)
    assertThrows<CalculationDataHasChangedError> {
      calculationTransactionalService.findCalculationResultsByCalculationReference(
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

  companion object {
    private val originalSentence = SentenceAndOffences(
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
      firstName = "Harry",
      lastName = "Houdini",
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
      PrisonApiSourceData(listOf(originalSentence), prisonerDetails, adjustments, emptyList(), null, null)
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
    val FAKE_TOKEN: Jwt = Jwt
      .withTokenValue("123")
      .header("header1", "value1")
      .claim("claim1", "value1")
      .build()
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
    val CALCULATION_REQUEST = CalculationRequest(
      calculationReference = CALCULATION_REFERENCE,
      prisonerId = PRISONER_ID,
      bookingId = BOOKING_ID,
    )

    val INPUT_DATA: JsonNode =
      JacksonUtil.toJsonNode(
        "{\"calculateErsed\": false, \"offender\":{\"reference\":\"A1234AJ\",\"dateOfBirth\":\"1980-01-01\",\"isActiveSexOffender\":false}," +
          "\"sentences\":[{\"type\":\"StandardSentence\",\"offence\":{\"committedAt\":\"2021-02-03\"," +
          "\"isScheduleFifteen\":false,\"isScheduleFifteenMaximumLife\":false,\"isPcscSds\":false,\"isPcscSec250\":false," +
          "\"isPcscSdsPlus\":false,\"offenceCode\":null},\"duration\":{\"durationElements\":{\"DAYS\":0,\"WEEKS\":0,\"MONTHS\":0,\"YEARS\":5}}," +
          "\"sentencedAt\":\"2021-02-03\",\"identifier\":\"5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa\"," +
          "\"consecutiveSentenceUUIDs\":[],\"caseSequence\":1,\"lineSequence\":2,\"caseReference\":null," +
          "\"recallType\":null,\"section250\":false}],\"adjustments\":{},\"returnToCustodyDate\":null,\"fixedTermRecallDetails\":null," +
          "\"bookingId\":12345}",
      )

    val CALCULATION_REASON = CalculationReason(-1, true, false, "Reason", false, nomisReason = "UPDATE")

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
    )

    val BOOKING = Booking(OFFENDER, listOf(StandardSENTENCE), Adjustments(), null, null, BOOKING_ID)
  }
}

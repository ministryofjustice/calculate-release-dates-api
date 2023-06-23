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
import org.mockito.Mockito
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationOutcome
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.CONFIRMED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus.PRELIMINARY
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ERSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.exceptions.PreconditionFailedException
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculatedReleaseDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationFragments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHoliday
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.RegionBankHolidays
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.resource.JsonTransformation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationService
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.Optional
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalculationTransactionalServiceTest {
  private val jsonTransformation = JsonTransformation()
  private val hdcedConfiguration = HdcedCalculator.HdcedConfiguration(12, ChronoUnit.WEEKS, 4, ChronoUnit.YEARS, 14, 720, ChronoUnit.DAYS, 179)
  private val hdcedCalculator = HdcedCalculator(hdcedConfiguration)
  private val bankHolidayService = mock<BankHolidayService>()
  private val workingDayService = WorkingDayService(bankHolidayService)
  private val tusedCalculator = TusedCalculator(workingDayService)
  private val sentenceAdjustedCalculationService = SentenceAdjustedCalculationService(hdcedCalculator, tusedCalculator)
  private val sentenceCalculationService = SentenceCalculationService(sentenceAdjustedCalculationService)
  private val sentencesExtractionService = SentencesExtractionService()
  private val sentenceIdentificationService = SentenceIdentificationService(hdcedCalculator, tusedCalculator)
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

  private val calculationTransactionalService =
    CalculationTransactionalService(
      calculationRequestRepository,
      calculationOutcomeRepository,
      TestUtil.objectMapper(),
      prisonService,
      prisonApiDataMapper,
      calculationService,
      bookingService,
      validationService,
      eventService,
      serviceUserService,
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

  @ParameterizedTest
  @CsvFileSource(resources = ["/test_data/calculation-service-examples.csv"], numLinesToSkip = 1)
  fun `Test Example`(exampleType: String, exampleNumber: String, error: String?) {
    log.info("Testing example $exampleType/$exampleNumber")
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)

    val booking = jsonTransformation.loadBooking("$exampleType/$exampleNumber")
    val calculatedReleaseDates: CalculatedReleaseDates
    try {
      calculatedReleaseDates = calculationTransactionalService.calculate(booking, PRELIMINARY, fakeSourceData, null)
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
      calculationBreakdown = calculationTransactionalService.calculateWithBreakdown(booking, CalculatedReleaseDates(calculation.dates, -1, -1, "", PRELIMINARY))
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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(fakeSourceData)
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<PreconditionFailedException> {
      calculationTransactionalService.validateAndConfirmCalculation(CALCULATION_REQUEST_ID, CalculationFragments(""))
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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(fakeSourceData)
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)

    val exception = assertThrows<EntityNotFoundException> {
      calculationTransactionalService.validateAndConfirmCalculation(CALCULATION_REQUEST_ID, CalculationFragments(""))
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
    whenever(prisonService.getPrisonApiSourceData(CALCULATION_REQUEST_WITH_OUTCOMES.prisonerId)).thenReturn(fakeSourceData)
    whenever(bookingService.getBooking(fakeSourceData, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(CALCULATION_REQUEST_ID)).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))

    assertDoesNotThrow { calculationTransactionalService.validateAndConfirmCalculation(CALCULATION_REQUEST_ID, CalculationFragments("")) }
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
      calculationTransactionalService.writeToNomisAndPublishEvent(PRISONER_ID, BOOKING, BOOKING_CALCULATION)
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
      calculationTransactionalService.writeToNomisAndPublishEvent(PRISONER_ID, BOOKING, BOOKING_CALCULATION)
    } catch (ex: Exception) {
      fail("Exception was thrown!")
    }
  }

  @BeforeEach
  fun beforeAll() {
    Mockito.`when`(bankHolidayService.getBankHolidays()).thenReturn(cachedBankHolidays)
  }

  companion object {
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
    )

    val BOOKING_CALCULATION = CalculatedReleaseDates(
      dates = mutableMapOf(CRD to CALCULATION_OUTCOME_CRD.outcomeDate!!, SED to THIRD_FEB_2021),
      calculationRequestId = CALCULATION_REQUEST_ID,
      bookingId = BOOKING_ID,
      prisonerId = PRISONER_ID,
      calculationStatus = CONFIRMED,
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

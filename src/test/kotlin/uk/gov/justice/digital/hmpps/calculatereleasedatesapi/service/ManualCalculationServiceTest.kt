package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
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
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.NormalisedSentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.util.BookingHelperTest
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit
import java.util.*

class ManualCalculationServiceTest {
  private val prisonService = mock<PrisonService>()
  private val bookingService = mock<BookingService>()
  private val calculationOutcomeRepository = mock<CalculationOutcomeRepository>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val calculationReasonRepository = mock<CalculationReasonRepository>()
  private val objectMapper = TestUtil.objectMapper()
  private val eventService = mock<EventService>()
  private val serviceUserService = mock<ServiceUserService>()
  private val nomisCommentService = mock<NomisCommentService>()
  private val bookingCalculationService = mock<BookingCalculationService>()
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
    bookingCalculationService,
  )
  private val calculationRequestArgumentCaptor = argumentCaptor<CalculationRequest>()

  @Nested
  inner class CalculateESLTests {
    @Test
    fun `Check ESL is calculated correctly for determinate sentences without SED`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
      )
      var workingBooking = BOOKING.copy(
        sentences = listOf(
          sentenceOne,
        ),
      )
      workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
      whenever(bookingCalculationService.identify(any())).thenReturn(workingBooking)
      whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(workingBooking)
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.NP.name), false, SDSEarlyReleaseExclusionType.NO),
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE, false, SDSEarlyReleaseExclusionType.NO),
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
    fun `Check ESL is calculated correctly for indeterminate sentences`() {
      // Arrange
      val sentenceOne = StandardSENTENCE.copy(
        consecutiveSentenceUUIDs = emptyList(),
        sentencedAt = LocalDate.of(2022, 1, 1),
      )
      var workingBooking = BOOKING.copy(
        sentences = listOf(
          sentenceOne,
        ),
      )
      workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
      whenever(bookingCalculationService.identify(any())).thenReturn(workingBooking)
      whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(workingBooking)
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name), false, SDSEarlyReleaseExclusionType.NO),
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE, false, SDSEarlyReleaseExclusionType.NO),
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
      var workingBooking = BOOKING.copy(
        sentences = listOf(
          sentenceOne,
          sentenceTwo,
        ),
      )
      workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
      whenever(bookingCalculationService.identify(any())).thenReturn(workingBooking)
      whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(workingBooking)

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
      var workingBooking = BOOKING.copy(
        sentences = listOf(
          sentenceOne,
        ),
      )
      workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
      whenever(bookingCalculationService.identify(any())).thenReturn(workingBooking)
      whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(workingBooking)

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
      var workingBooking = BOOKING.copy(
        sentences = listOf(
          consecutiveSentenceOne,
          consecutiveSentenceTwo,
        ),
      )
      workingBooking = BookingHelperTest().createConsecutiveSentences(workingBooking)
      whenever(bookingCalculationService.identify(any())).thenReturn(workingBooking)
      whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(workingBooking)

      // Act
      val result = manualCalculationService.calculateEffectiveSentenceLength(BOOKING, MANUAL_ENTRY)

      // Assert
      assertThat(result).isEqualTo(Period.of(4, 10, 29))
    }
  }

  @Nested
  inner class IndeterminateSentencesTests {
    @Test
    fun `Check the presence of indeterminate sentences returns true`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name), false, SDSEarlyReleaseExclusionType.NO),
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE, false, SDSEarlyReleaseExclusionType.NO),
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isTrue()
    }

    @Test
    fun `Check the absence of indeterminate sentences returns false`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR.name), false, SDSEarlyReleaseExclusionType.NO),
          SentenceAndOffenceWithReleaseArrangements(BASE_DETERMINATE_SENTENCE, false, SDSEarlyReleaseExclusionType.NO),
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isFalse()
    }
  }

  @Test
  fun `Check noDates is set correctly when indeterminate`() {
    val sentenceOne = StandardSENTENCE.copy(
      consecutiveSentenceUUIDs = emptyList(),
      sentencedAt = LocalDate.of(2022, 1, 1),
    )
    var booking = BOOKING.copy(
      sentences = listOf(
        sentenceOne,
      ),
    )
    booking = BookingHelperTest().createConsecutiveSentences(booking)
    whenever(bookingCalculationService.identify(any())).thenReturn(booking)
    whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(booking)
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
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
    val sentenceOne = StandardSENTENCE.copy(
      consecutiveSentenceUUIDs = emptyList(),
      sentencedAt = LocalDate.of(2022, 1, 1),
    )
    var booking = BOOKING.copy(
      sentences = listOf(
        sentenceOne,
      ),
    )
    booking = BookingHelperTest().createConsecutiveSentences(booking)
    whenever(bookingCalculationService.identify(any())).thenReturn(booking)
    whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(booking)
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")

    val manualCalcRequest = ManualEntrySelectedDate(ReleaseDateType.CRD, "CRD also known as the Conditional Release Date", SubmittedDate(3, 3, 2023))
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
    val sentenceOne = StandardSENTENCE.copy(
      consecutiveSentenceUUIDs = emptyList(),
      sentencedAt = LocalDate.of(2022, 1, 1),
    )
    var booking = BOOKING.copy(
      sentences = listOf(
        sentenceOne,
      ),
    )
    booking = BookingHelperTest().createConsecutiveSentences(booking)
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(bookingCalculationService.identify(any())).thenReturn(booking)
    whenever(bookingCalculationService.createConsecutiveSentences(any())).thenReturn(booking)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")

    val manualCalcRequest = ManualEntrySelectedDate(ReleaseDateType.CRD, "CRD also known as the Conditional Release Date", SubmittedDate(3, 3, 2023))
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, true)
    verify(calculationRequestRepository).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_OVERRIDE)
  }

  @Test
  fun `Check type is set to manual indeterminate when indeterminate sentences are present`() {
    val offence = OffenderOffence(1L, LocalDate.of(2015, 1, 1), null, "ADIMP", "description", listOf("A"))
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(
      listOf(
        SentenceAndOffenceWithReleaseArrangements(
          PrisonApiSentenceAndOffences(
            1,
            1,
            1,
            1,
            null,
            "A",
            "A",
            "LIFE",
            "",
            LocalDate.now(),
            offences = listOf(offence),
          ),
          offence,
          false,
          SDSEarlyReleaseExclusionType.NO,
        ),
      ),
    )

    val manualCalcRequest = ManualEntrySelectedDate(ReleaseDateType.CRD, "CRD also known as the Conditional Release Date", SubmittedDate(3, 3, 2023))
    val manualEntryRequest = ManualEntryRequest(listOf(manualCalcRequest), 1L, "")

    manualCalculationService.storeManualCalculation(PRISONER_ID, manualEntryRequest, false)
    verify(calculationRequestRepository).save(calculationRequestArgumentCaptor.capture())
    val actualRequest = calculationRequestArgumentCaptor.firstValue
    assertThat(actualRequest.calculationType).isEqualTo(CalculationType.MANUAL_INDETERMINATE)
  }

  private companion object {
    private const val BOOKING_ID = 12345L
    private val THIRD_FEB_2021 = LocalDate.of(2021, 2, 3)
    private val FIVE_YEAR_DURATION = Duration(mutableMapOf(ChronoUnit.DAYS to 0L, ChronoUnit.WEEKS to 0L, ChronoUnit.MONTHS to 0L, ChronoUnit.YEARS to 5L))
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
    private val FAKE_SOURCE_DATA = PrisonApiSourceData(
      emptyList(),
      PrisonerDetails(offenderNo = "", bookingId = 1, dateOfBirth = LocalDate.of(1, 2, 3)),
      BookingAndSentenceAdjustments(
        emptyList(),
        emptyList(),
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

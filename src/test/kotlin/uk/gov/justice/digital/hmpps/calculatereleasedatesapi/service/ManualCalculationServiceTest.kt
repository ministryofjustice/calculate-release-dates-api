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
import org.springframework.security.oauth2.jwt.Jwt
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.TestUtil
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationReason
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationStatus
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntryRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ManualEntrySelectedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SubmittedDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderKeyDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffences
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.UpdateOffenderDates
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationOutcomeRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.UUID

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
  )
  private val calculationRequestArgumentCaptor = argumentCaptor<CalculationRequest>()

  @Nested
  inner class IndeterminateSentencesTests {
    @Test
    fun `Check the presence of indeterminate sentences returns true`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.TWENTY.name),
          BASE_DETERMINATE_SENTENCE,
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isTrue()
    }

    @Test
    fun `Check the absence of indeterminate sentences returns false`() {
      whenever(prisonService.getSentencesAndOffences(BOOKING_ID)).thenReturn(
        listOf(
          BASE_DETERMINATE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.FTR.name),
          BASE_DETERMINATE_SENTENCE,
        ),
      )

      val hasIndeterminateSentences = manualCalculationService.hasIndeterminateSentences(BOOKING_ID)

      assertThat(hasIndeterminateSentences).isFalse()
    }
  }

  @Test
  fun `Check noDates is set correctly when indeterminate`() {
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
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
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
    whenever(prisonService.getPrisonApiSourceData(PRISONER_ID)).thenReturn(FAKE_SOURCE_DATA)
    whenever(calculationRequestRepository.save(any())).thenReturn(CALCULATION_REQUEST_WITH_OUTCOMES)
    whenever(calculationRequestRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REQUEST_WITH_OUTCOMES))
    whenever(calculationReasonRepository.findById(any())).thenReturn(Optional.of(CALCULATION_REASON))
    whenever(bookingService.getBooking(FAKE_SOURCE_DATA, CalculationUserInputs())).thenReturn(BOOKING)
    whenever(serviceUserService.getUsername()).thenReturn(USERNAME)
    whenever(nomisCommentService.getManualNomisComment(any(), any(), any())).thenReturn("The NOMIS Reason")
    whenever(prisonService.getSentencesAndOffences(anyLong(), eq(true))).thenReturn(listOf(SentenceAndOffences(1, 1, 1, 1, null, "A", "A", "LIFE", "", LocalDate.now())))

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

    private val BASE_DETERMINATE_SENTENCE = SentenceAndOffences(
      bookingId = BOOKING_ID,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      sentenceDate = LocalDate.of(2022, 1, 1),
      sentenceStatus = "IMP",
      sentenceCategory = "CAT",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "ADMIP",
    )
    private val FAKE_TOKEN: Jwt = Jwt
      .withTokenValue("123")
      .header("header1", "value1")
      .claim("claim1", "value1")
      .build()
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
    )
    private val BOOKING = Booking(OFFENDER, listOf(StandardSENTENCE), Adjustments(), null, null, BOOKING_ID)

    const val USERNAME = "user1"
  }
}

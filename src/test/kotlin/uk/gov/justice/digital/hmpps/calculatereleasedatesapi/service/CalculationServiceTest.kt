package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.CalculationRule
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.CRD
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.ESED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.HDCED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.LED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.SLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ReleaseDateType.TUSED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Duration
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ExternalSentenceId
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelope
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.OperativeSentenceEnvelopeSource
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.PreviouslyRecordedSLED
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Recall
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecallType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ReleaseDateCalculationBreakdown
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.StandardDeterminateSentence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentenceIdentificationService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.timeline.BookingTimelineService
import java.time.LocalDate
import java.time.Period
import java.time.temporal.ChronoUnit.DAYS
import java.time.temporal.ChronoUnit.MONTHS
import java.time.temporal.ChronoUnit.WEEKS
import java.time.temporal.ChronoUnit.YEARS
import java.util.UUID

@ExtendWith(MockitoExtension::class)
class CalculationServiceTest {

  private val sentenceIdentificationService = mock<SentenceIdentificationService>()
  private val bookingTimelineService = mock<BookingTimelineService>(lenient = false)
  private val previouslyRecordedSLEDService = mock<PreviouslyRecordedSLEDService>()
  private val sentenceLevelDatesService = mock<SentenceLevelDatesService>()
  private val featureToggles = FeatureToggles(storeSentenceLevelDates = true, storeOperativeSentenceEnvelope = true)

  private val service = CalculationService(sentenceIdentificationService, bookingTimelineService, featureToggles, previouslyRecordedSLEDService, sentenceLevelDatesService)

  @Test
  fun `should skip checking for dominant historic dates if user requested it not to be used`() {
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(CALCULATION_OUTPUT)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = false), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      CALCULATION_OUTPUT.copy(
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 1826,
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )

    verify(previouslyRecordedSLEDService, never()).findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())
  }

  @Test
  fun `should use dominant historic SLED with TUSED retained if user requested it be used and one is found that is before the TUSED`() {
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(CALCULATION_OUTPUT)
    val expectedPreviouslyRecordedSled = PreviouslyRecordedSLED(
      previouslyRecordedSLEDDate = LocalDate.of(2026, 6, 15),
      calculatedDate = LocalDate.of(2026, 2, 2),
      previouslyRecordedSLEDCalculationRequestId = 99999,
    )
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(expectedPreviouslyRecordedSled)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      CalculationOutput(
        listOf(SDS_SENTENCE),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(1, 0, 0),
          dates = mapOf(
            SLED to LocalDate.of(2026, 6, 15),
            CRD to LocalDate.of(2023, 8, 4),
            HDCED to LocalDate.of(2023, 2, 6),
            TUSED to LocalDate.of(2027, 2, 2),
            ESED to LocalDate.of(2026, 2, 2),
          ),
          usedPreviouslyRecordedSLED = expectedPreviouslyRecordedSled,
          breakdownByReleaseDateType = mapOf(
            SLED to ReleaseDateCalculationBreakdown(
              releaseDate = LocalDate.of(2026, 6, 15),
              unadjustedDate = LocalDate.of(2026, 2, 2),
              rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
            ),
            TUSED to ReleaseDateCalculationBreakdown(
              releaseDate = LocalDate.of(2027, 2, 2),
              unadjustedDate = LocalDate.of(2027, 2, 2),
              rules = emptySet(),
            ),
          ),
        ),
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 1959, // from historic SLED
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )

    verify(previouslyRecordedSLEDService).findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())
  }

  @Test
  fun `should use dominant historic SLED with TUSED removed if user requested it be used and one is found that is after the TUSED`() {
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(CALCULATION_OUTPUT)
    val expectedPreviouslyRecordedSled = PreviouslyRecordedSLED(
      previouslyRecordedSLEDDate = LocalDate.of(2028, 6, 15),
      calculatedDate = LocalDate.of(2026, 2, 2),
      previouslyRecordedSLEDCalculationRequestId = 99999,
    )
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(expectedPreviouslyRecordedSled)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      CalculationOutput(
        listOf(SDS_SENTENCE),
        emptyList(),
        CalculationResult(
          effectiveSentenceLength = Period.of(1, 0, 0),
          dates = mapOf(
            SLED to LocalDate.of(2028, 6, 15),
            CRD to LocalDate.of(2023, 8, 4),
            HDCED to LocalDate.of(2023, 2, 6),
            ESED to LocalDate.of(2026, 2, 2),
          ),
          usedPreviouslyRecordedSLED = expectedPreviouslyRecordedSled,
          breakdownByReleaseDateType = mapOf(
            SLED to ReleaseDateCalculationBreakdown(
              releaseDate = LocalDate.of(2028, 6, 15),
              unadjustedDate = LocalDate.of(2026, 2, 2),
              rules = setOf(CalculationRule.PREVIOUSLY_RECORDED_SLED_USED),
            ),
          ),
        ),
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 2690, // from historic SLED
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )

    verify(previouslyRecordedSLEDService).findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())
  }

  @Test
  fun `should handle no dominant historic SLED even if user requested it`() {
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(CALCULATION_OUTPUT)
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(null)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      CALCULATION_OUTPUT.copy(
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 1826,
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )

    verify(previouslyRecordedSLEDService).findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())
  }

  @Test
  fun `should mark operative sentence envelope with SDS+ if there was an SDS plus sentence`() {
    val calculationOutputWithSdsPlus = CALCULATION_OUTPUT.copy(
      sentences = listOf(
        SDS_SENTENCE.copy(
          releaseArrangements = SDSReleaseArrangements(
            isSDSPlus = true,
            isSDSPlusEligibleSentenceTypeLengthAndOffence = true,
            isSection250 = false,
            sdsEarlyReleaseExclusions = emptyList(),
          ),
        ),
      ),
    )
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculationOutputWithSdsPlus)
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(null)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      calculationOutputWithSdsPlus.copy(
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 1826,
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = true,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )
  }

  @Test
  fun `should mark operative sentence envelope with recall if there was a recall sentence`() {
    val calculationOutputWithRecall = CALCULATION_OUTPUT.copy(sentences = listOf(SDS_SENTENCE.copy(recall = Recall(recallType = RecallType.FIXED_TERM_RECALL_56))))
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculationOutputWithRecall)
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(null)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      calculationOutputWithRecall.copy(
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 1826,
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = true,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )
  }

  @Test
  fun `should use SED for operative sentence envelope if there is no SLED`() {
    val calcResultWithSedAndLed = CalculationResult(
      effectiveSentenceLength = Period.of(1, 0, 0),
      dates = mapOf(
        SED to LocalDate.of(2027, 2, 2),
        LED to LocalDate.of(2026, 2, 2),
        CRD to LocalDate.of(2023, 8, 4),
        HDCED to LocalDate.of(2023, 2, 6),
        TUSED to LocalDate.of(2027, 2, 2),
        ESED to LocalDate.of(2026, 2, 2),
      ),
      breakdownByReleaseDateType = mapOf(
        SED to ReleaseDateCalculationBreakdown(
          releaseDate = LocalDate.of(2027, 2, 2),
          unadjustedDate = LocalDate.of(2026, 2, 2),
          rules = emptySet(),
        ),
        LED to ReleaseDateCalculationBreakdown(
          releaseDate = LocalDate.of(2026, 2, 2),
          unadjustedDate = LocalDate.of(2026, 2, 2),
          rules = emptySet(),
        ),
        TUSED to ReleaseDateCalculationBreakdown(
          releaseDate = LocalDate.of(2027, 2, 2),
          unadjustedDate = LocalDate.of(2027, 2, 2),
          rules = emptySet(),
        ),
      ),
    )
    val calculationOutputWithSeparateSedAndLed = CALCULATION_OUTPUT.copy(calculationResult = calcResultWithSedAndLed)
    whenever(bookingTimelineService.calculate(anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull(), anyOrNull()))
      .thenReturn(calculationOutputWithSeparateSedAndLed)
    whenever(previouslyRecordedSLEDService.findPreviouslyRecordedSLEDThatShouldOverrideTheCalculatedSLED(any(), any())).thenReturn(null)

    val result = service.calculateReleaseDates(BOOKING, CalculationUserInputs(usePreviouslyRecordedSLEDIfFound = true), calculateSentenceLevelDates = false)

    assertThat(result).isEqualTo(
      calculationOutputWithSeparateSedAndLed.copy(
        operativeSentenceEnvelope = OperativeSentenceEnvelope(
          sentenceEnvelopeLengthInDays = 2191,
          earliestSentenceStartDate = LocalDate.of(2021, 2, 3),
          isPostRecallSentenceEnvelope = false,
          containsAnSDSPlusSentence = false,
          sentenceEnvelopeSource = OperativeSentenceEnvelopeSource.CRDS,
          bookingId = BOOKING_ID,
        ),
      ),
    )
  }

  companion object {

    private const val BOOKING_ID = 123456L

    private val OFFENDER = Offender("A1234BC", LocalDate.of(1980, 1, 1))

    private val SDS_SENTENCE = StandardDeterminateSentence(
      sentencedAt = LocalDate.of(2021, 2, 3),
      duration = Duration(mutableMapOf(DAYS to 0L, WEEKS to 0L, MONTHS to 0L, YEARS to 5L)),
      offence = Offence(committedAt = LocalDate.of(2021, 2, 3)),
      identifier = UUID.fromString("5ac7a5ae-fa7b-4b57-a44f-8eddde24f5fa"),
      caseSequence = 1,
      lineSequence = 2,
      externalSentenceId = ExternalSentenceId(sentenceSequence = 1, bookingId = BOOKING_ID),
      releaseArrangements = SDSReleaseArrangements(
        isSDSPlus = false,
        isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
        isSection250 = false,
        sdsEarlyReleaseExclusions = emptyList(),
      ),
    )

    private val BOOKING = Booking(OFFENDER, listOf(SDS_SENTENCE), Adjustments(), null, null, BOOKING_ID)

    private val CALCULATION_OUTPUT = CalculationOutput(
      listOf(SDS_SENTENCE),
      emptyList(),
      CalculationResult(
        effectiveSentenceLength = Period.of(1, 0, 0),
        dates = mapOf(
          SLED to LocalDate.of(2026, 2, 2),
          CRD to LocalDate.of(2023, 8, 4),
          HDCED to LocalDate.of(2023, 2, 6),
          TUSED to LocalDate.of(2027, 2, 2),
          ESED to LocalDate.of(2026, 2, 2),
        ),
        breakdownByReleaseDateType = mapOf(
          SLED to ReleaseDateCalculationBreakdown(
            releaseDate = LocalDate.of(2026, 2, 2),
            unadjustedDate = LocalDate.of(2026, 2, 2),
            rules = emptySet(),
          ),
          TUSED to ReleaseDateCalculationBreakdown(
            releaseDate = LocalDate.of(2027, 2, 2),
            unadjustedDate = LocalDate.of(2027, 2, 2),
            rules = emptySet(),
          ),
        ),
      ),
    )
  }
}

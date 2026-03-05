package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationUserInputs
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallDecision
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.RecordARecallRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonPeriod
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.PrisonerInPrisonSummary
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationReasonRepository
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.service.ValidationService
import java.time.LocalDate
import java.util.*

@ExtendWith(MockitoExtension::class)
class RecordARecallDecisionServiceTest {

  private val prisonService: PrisonService = mock(PrisonService::class.java)
  private val calculationSourceDataService: CalculationSourceDataService = mock(CalculationSourceDataService::class.java)
  private val calculationTransactionalService: CalculationTransactionalService = mock(CalculationTransactionalService::class.java)
  private val calculationReasonRepository: CalculationReasonRepository = mock(CalculationReasonRepository::class.java)
  private val validationService: ValidationService = mock(ValidationService::class.java)
  private val bookingService: BookingService = mock(BookingService::class.java)
  private val nomisSyncMappingApiClient: NomisSyncMappingApiClient = mock(NomisSyncMappingApiClient::class.java)

  private lateinit var underTest: RecordARecallDecisionService

  @BeforeEach
  fun setUp() {
    underTest = RecordARecallDecisionService(
      prisonService = prisonService,
      calculationSourceDataService = calculationSourceDataService,
      calculationTransactionalService = calculationTransactionalService,
      calculationReasonRepository = calculationReasonRepository,
      validationService = validationService,
      bookingService = bookingService,
      nomisSyncMappingApiClient = nomisSyncMappingApiClient,
      featureToggles = FeatureToggles(),
    )
  }

  @Nested
  inner class ValidateTests {
    @Test
    fun `validate sets penultimateCriticalMessages when only penultimate booking has critical errors`() {
      val inPrisonSummary = mock(PrisonerInPrisonSummary::class.java)
      val latest = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(bookingSequence).thenReturn(1)
      }
      val penultimate = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(bookingSequence).thenReturn(2)
      }
      whenever(inPrisonSummary.prisonPeriod).thenReturn(listOf(latest, penultimate))
      whenever(prisonService.getPrisonerInPrisonSummary(PRISONER_ID)).thenReturn(inPrisonSummary)

      val latestSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2020, 1, 1))
      }
      val penultimateSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2019, 1, 1))
      }

      val sourceData = baseSourceData.copy(sentenceAndOffences = listOf(latestSentence, penultimateSentence))

      whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any()))
        .thenReturn(sourceData)

      whenever(validationService.validate(any(), any<CalculationUserInputs>(), eq(ValidationOrder.INVALID)))
        .thenReturn(emptyList())

      val critical =
        ValidationMessage(code = RecordARecallDecisionService.criticalValidationErrors.first(), message = "critical")
      whenever(
        validationService.validate(
          argThat { sentenceAndOffences.all { it.bookingId == PENULTIMATE_BOOKING_ID } },
          any<CalculationUserInputs>(),
          eq(ValidationOrder.INVALID),
        ),
      ).thenReturn(listOf(critical))

      val result = underTest.validate(PRISONER_ID)

      assertThat(result.latestCriticalMessages).isEmpty()
      assertThat(result.latestOtherMessages).isEmpty()
      assertThat(result.penultimateCriticalMessages).isNotEmpty()
      assertThat(result.penultimateOtherMessages).isEmpty()
    }

    @Test
    fun `validate sets penultimateOtherMessages when only penultimate booking has other errors`() {
      val inPrisonSummary = mock(PrisonerInPrisonSummary::class.java)
      val latest = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(bookingSequence).thenReturn(1)
      }
      val penultimate = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(bookingSequence).thenReturn(2)
      }
      whenever(inPrisonSummary.prisonPeriod).thenReturn(listOf(latest, penultimate))
      whenever(prisonService.getPrisonerInPrisonSummary(PRISONER_ID)).thenReturn(inPrisonSummary)

      val latestSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2020, 1, 1))
      }
      val penultimateSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2019, 1, 1))
      }

      val sourceData = baseSourceData.copy(sentenceAndOffences = listOf(latestSentence, penultimateSentence))

      whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any()))
        .thenReturn(sourceData)

      whenever(validationService.validate(any(), any<CalculationUserInputs>(), eq(ValidationOrder.INVALID)))
        .thenReturn(emptyList())

      val nonCriticalCode =
        ValidationCode.entries.first { it !in RecordARecallDecisionService.criticalValidationErrors }
      val other = ValidationMessage(
        code = nonCriticalCode,
        message = "other",
      )

      whenever(
        validationService.validate(
          argThat { sentenceAndOffences.all { it.bookingId == PENULTIMATE_BOOKING_ID } },
          any<CalculationUserInputs>(),
          eq(ValidationOrder.INVALID),
        ),
      ).thenReturn(listOf(other))

      val result = underTest.validate(PRISONER_ID)

      assertThat(result.latestCriticalMessages).isEmpty()
      assertThat(result.latestOtherMessages).isEmpty()
      assertThat(result.penultimateCriticalMessages).isEmpty()
      assertThat(result.penultimateOtherMessages).isNotEmpty()
    }

    @Test
    fun `validate sets earliestSentenceDate to null when there are no sentences`() {
      val inPrisonSummary = mock(PrisonerInPrisonSummary::class.java)
      whenever(inPrisonSummary.prisonPeriod).thenReturn(null)
      whenever(prisonService.getPrisonerInPrisonSummary(PRISONER_ID)).thenReturn(inPrisonSummary)
      whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any()))
        .thenReturn(baseSourceData)
      whenever(validationService.validate(any(), any<CalculationUserInputs>(), eq(ValidationOrder.INVALID)))
        .thenReturn(emptyList())

      val result = underTest.validate(PRISONER_ID)

      assertThat(result.earliestSentenceDate).isNull()
    }
  }

  @Nested
  inner class MakeDecisionTests {
    @Test
    fun `makeRecallDecision returns CRITICAL_ERRORS early when latest booking has critical errors`() {
      val inPrisonSummary = mock(PrisonerInPrisonSummary::class.java)

      val latest = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(bookingSequence).thenReturn(1)
      }
      val penultimate = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(bookingSequence).thenReturn(2)
      }

      whenever(inPrisonSummary.prisonPeriod).thenReturn(listOf(latest, penultimate))
      whenever(prisonService.getPrisonerInPrisonSummary(PRISONER_ID)).thenReturn(inPrisonSummary)

      val latestSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2020, 1, 1))
      }
      val penultimateSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
      }

      val sourceData = baseSourceData.copy(sentenceAndOffences = listOf(latestSentence, penultimateSentence))

      whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any()))
        .thenReturn(sourceData)

      val critical = ValidationMessage(
        code = RecordARecallDecisionService.criticalValidationErrors.first(),
        message = "critical",
      )
      whenever(validationService.validate(any(), any<CalculationUserInputs>(), eq(ValidationOrder.INVALID)))
        .thenReturn(listOf(critical))

      val result = underTest.makeRecallDecision(
        prisonerId = PRISONER_ID,
        recordARecallRequest = RecordARecallRequest(
          revocationDate = LocalDate.of(2025, 1, 1),
          recallId = UUID.randomUUID(),
        ),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.CRITICAL_ERRORS)
      assertThat(result.validationMessages).isNotEmpty()
    }

    @Test
    fun `makeRecallDecision returns VALIDATION when only penultimate booking has critical errors`() {
      val inPrisonSummary = mock(PrisonerInPrisonSummary::class.java)

      val latest = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(bookingSequence).thenReturn(1)
      }
      val penultimate = mock(PrisonPeriod::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(bookingSequence).thenReturn(2)
      }

      whenever(inPrisonSummary.prisonPeriod).thenReturn(listOf(latest, penultimate))
      whenever(prisonService.getPrisonerInPrisonSummary(PRISONER_ID)).thenReturn(inPrisonSummary)

      val latestSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(LATEST_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2020, 1, 1))
      }
      val penultimateSentence = mock(SentenceAndOffenceWithReleaseArrangements::class.java).apply {
        whenever(bookingId).thenReturn(PENULTIMATE_BOOKING_ID)
        whenever(sentenceDate).thenReturn(LocalDate.of(2019, 1, 1))
      }

      val sourceData = baseSourceData.copy(sentenceAndOffences = listOf(latestSentence, penultimateSentence))

      whenever(calculationSourceDataService.getCalculationSourceData(eq(PRISONER_ID), any(), any()))
        .thenReturn(sourceData)

      val critical = ValidationMessage(
        code = RecordARecallDecisionService.criticalValidationErrors.first(),
        message = "critical",
      )

      whenever(
        validationService.validate(
          argThat { sentenceAndOffences.all { it.bookingId == LATEST_BOOKING_ID } },
          any<CalculationUserInputs>(),
          eq(ValidationOrder.INVALID),
        ),
      ).thenReturn(emptyList())

      whenever(
        validationService.validate(
          argThat { sentenceAndOffences.any { it.bookingId == PENULTIMATE_BOOKING_ID } },
          any<CalculationUserInputs>(),
          eq(ValidationOrder.INVALID),
        ),
      ).thenReturn(listOf(critical))

      val result = underTest.makeRecallDecision(
        prisonerId = PRISONER_ID,
        recordARecallRequest = RecordARecallRequest(
          revocationDate = LocalDate.of(2025, 1, 1),
          recallId = UUID.randomUUID(),
        ),
      )

      assertThat(result.decision).isEqualTo(RecordARecallDecision.VALIDATION)
      assertThat(result.validationMessages).isNotEmpty()
    }
  }

  private companion object {
    const val PRISONER_ID = "A1234BC"
    const val LATEST_BOOKING_ID = 2L
    const val PENULTIMATE_BOOKING_ID = 1L
    private val baseSourceData = CalculationSourceData(
      sentenceAndOffences = emptyList(),
      offenderFinePayments = emptyList(),
      prisonerDetails = PrisonerDetails(
        bookingId = LATEST_BOOKING_ID,
        offenderNo = PRISONER_ID,
        dateOfBirth = LocalDate.of(1990, 1, 1),
      ),
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      returnToCustodyDate = null,
    )
  }
}

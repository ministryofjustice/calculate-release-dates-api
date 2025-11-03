package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustmentAnalysisResult
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedBookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AnalysedSentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.*

class ThingsToDoServiceTest {
  private val adjustmentsService = mock<AdjustmentsService>()
  private val prisonService = mock<PrisonService>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val sourceDataMapper = mock<SourceDataMapper>()
  private val calculationSourceDataService = mock<CalculationSourceDataService>()
  private val thingsToDoService = ThingsToDoService(
    adjustmentsService,
    prisonService,
    calculationRequestRepository,
    sourceDataMapper,
    calculationSourceDataService,
  )

  @BeforeEach
  fun setUp() {
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(BASE_SOURCE_DATA)
    whenever(prisonService.getOffenderDetail(NOMS_ID)).thenReturn(PRISONER_DETAILS)
  }

  @Test
  fun `should require a calc if there is no previous calc`() {
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(BOOKING_ID, "CONFIRMED")).thenReturn(Optional.empty())
    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are sentences added`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(BASE_SOURCE_DATA)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are sentences removed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default()))
      .thenReturn(BASE_SOURCE_DATA.copy(sentenceAndOffences = listOf(BASE_SENTENCE)))

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from consec to concurrent`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default()))
      .thenReturn(
        BASE_SOURCE_DATA.copy(
          sentenceAndOffences =
          listOf(
            BASE_SENTENCE,
            BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = null),
          ),
        ),
      )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from concurrent to consec`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = null),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from consec on one sentence to another`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
          BASE_SENTENCE.copy(sentenceSequence = 3, lineSequence = 3, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
          BASE_SENTENCE.copy(sentenceSequence = 3, lineSequence = 3, consecutiveToSequence = 2),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from active to inactive`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "A"),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "I"),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from inactive to active`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "A"),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "I"),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence type changes`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.ADIMP.name),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.EDS21.name),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence date changes`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceDate = LocalDate.of(2025, 1, 2)),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceDate = LocalDate.of(2025, 2, 3)),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence terms change`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(
            terms = listOf(
              SentenceTerms(1, 0, 0, 0, "IMP"),
              SentenceTerms(0, 6, 0, 0, "LIC"),
            ),
          ),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(
            terms = listOf(
              SentenceTerms(1, 0, 0, 0, "IMP"),
              SentenceTerms(1, 0, 0, 0, "LIC"),
            ),
          ),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a fine amount is added`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = null)),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(10)),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a fine amount is removed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(10))),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(fineAmount = null),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a fine amount is changed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(10))),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(20)),
        ),
      ),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if return to custody date is added`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = null),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2001, 2, 3))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if return to custody date is removed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2000, 1, 2))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = null),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if return to custody date is changed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2000, 1, 2))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2001, 2, 3))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are new booking adjustments`() {
    hasPreviousCalc()
    hasTheSameSourceData()
    whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID))
      .thenReturn(
        AnalysedBookingAndSentenceAdjustments(
          listOf(
            BASE_BOOKING_ADJUSTMENT.copy(analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME),
            BASE_BOOKING_ADJUSTMENT.copy(type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED, analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW),
          ),
          emptyList(),
        ),
      )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are new sentences adjustments`() {
    hasPreviousCalc()
    hasTheSameSourceData()
    whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID))
      .thenReturn(
        AnalysedBookingAndSentenceAdjustments(
          emptyList(),
          listOf(
            BASE_SENTENCE_ADJUSTMENT.copy(analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME),
            BASE_SENTENCE_ADJUSTMENT.copy(type = SentenceAdjustmentType.TAGGED_BAIL, analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.NEW),
          ),
        ),
      )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are new fine payments`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = emptyList()),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are fine payments removed`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = emptyList()),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are fine payments updated`() {
    hasPreviousCalc()
    hasNoAdjustments()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(20)))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should not require a calc if there are no sentences`() {
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(BOOKING_ID, "CONFIRMED")).thenReturn(Optional.empty())
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(sentenceAndOffences = emptyList()),
    )

    assertThatHasNothingToDo()
  }

  @Test
  fun `should not require a calc if the sentences and offences are exactly the same`() {
    hasPreviousCalc()
    hasNoAdjustments()
    val sourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(sourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(sourceData)
    whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID))
      .thenReturn(
        AnalysedBookingAndSentenceAdjustments(
          listOf(
            BASE_BOOKING_ADJUSTMENT.copy(analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME),
          ),
          listOf(
            BASE_SENTENCE_ADJUSTMENT.copy(analysisResult = AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME),
          ),
        ),
      )

    assertThatHasNothingToDo()
  }

  private fun hasPreviousCalc() {
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(BOOKING_ID, "CONFIRMED")).thenReturn(Optional.of(CALC_REQUEST))
  }

  private fun hasNoAdjustments() {
    whenever(adjustmentsService.getAnalysedBookingAndSentenceAdjustments(BOOKING_ID)).thenReturn(AnalysedBookingAndSentenceAdjustments(emptyList(), emptyList()))
  }

  private fun hasTheSameSourceData() {
    val sourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(sourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, InactiveDataOptions.default())).thenReturn(sourceData)
  }

  private fun assertThatHasACalcToDo() {
    assertThat(thingsToDoService.getToDoList(NOMS_ID)).isEqualTo(
      ThingsToDo(
        prisonerId = NOMS_ID,
        thingsToDo = listOf(ToDoType.CALCULATION_REQUIRED),
      ),
    )
  }

  private fun assertThatHasNothingToDo() {
    3

    assertThat(thingsToDoService.getToDoList(NOMS_ID)).isEqualTo(
      ThingsToDo(
        prisonerId = NOMS_ID,
        thingsToDo = emptyList(),
      ),
    )
  }

  companion object {
    private const val NOMS_ID = "AA1234A"
    private const val BOOKING_ID = 1234L
    private val PRISONER_DETAILS = PrisonerDetails(
      bookingId = BOOKING_ID,
      offenderNo = NOMS_ID,
      firstName = "John",
      lastName = "Smith",
      dateOfBirth = LocalDate.of(1970, 1, 1),
    )
    private val CALC_REQUEST = CalculationRequest(
      id = 999,
      bookingId = BOOKING_ID,
      prisonerId = NOMS_ID,
    )
    private val FIRST_JAN_2015: LocalDate = LocalDate.of(2015, 1, 1)
    val BASE_OFFENCE = OffenderOffence(
      offenderChargeId = BOOKING_ID,
      offenceStartDate = FIRST_JAN_2015,
      offenceCode = "RR1",
      offenceDescription = "Littering",
    )
    private val BASE_SENTENCE = SentenceAndOffenceWithReleaseArrangements(
      bookingId = BOOKING_ID,
      sentenceSequence = 1,
      lineSequence = 1,
      caseSequence = 1,
      consecutiveToSequence = null,
      sentenceStatus = "A",
      sentenceCategory = "STANDARD",
      sentenceCalculationType = SentenceCalculationType.ADIMP.name,
      sentenceTypeDescription = "Standard Determinate",
      sentenceDate = FIRST_JAN_2015,
      terms = listOf(
        SentenceTerms(
          years = 5,
          months = 4,
          weeks = 3,
          days = 2,
        ),
      ),
      offence = BASE_OFFENCE,
      caseReference = null,
      courtDescription = null,
      courtTypeCode = null,
      fineAmount = null,
      revocationDates = emptyList(),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    private val BASE_BOOKING_ADJUSTMENT = AnalysedBookingAdjustment(true, LocalDate.of(2000, 1, 1), null, 1, BookingAdjustmentType.LAWFULLY_AT_LARGE, AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME)
    private val BASE_SENTENCE_ADJUSTMENT = AnalysedSentenceAdjustment(1, true, LocalDate.of(2000, 1, 1), null, 1, SentenceAdjustmentType.REMAND, AnalysedBookingAndSentenceAdjustmentAnalysisResult.SAME)
    private val BASE_SOURCE_DATA = CalculationSourceData(
      sentenceAndOffences = listOf(BASE_SENTENCE),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
  }
}

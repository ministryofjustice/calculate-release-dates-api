package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.entity.CalculationRequest
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.ToDoType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SDSEarlyReleaseExclusionType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.ThingsToDo
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderFinePayment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.ReturnToCustodyDate
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.repository.CalculationRequestRepository
import java.math.BigDecimal
import java.time.LocalDate
import java.util.Optional
import java.util.stream.Stream

class ThingsToDoServiceTest {
  private val prisonService = mock<PrisonService>()
  private val calculationRequestRepository = mock<CalculationRequestRepository>()
  private val sourceDataMapper = mock<SourceDataMapper>()
  private val calculationSourceDataService = mock<CalculationSourceDataService>()
  private val thingsToDoService = ThingsToDoService(
    prisonService,
    calculationRequestRepository,
    sourceDataMapper,
    calculationSourceDataService,
  )

  @BeforeEach
  fun setUp() {
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(BASE_SOURCE_DATA)
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(BASE_SOURCE_DATA)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default()))
      .thenReturn(BASE_SOURCE_DATA.copy(sentenceAndOffences = listOf(BASE_SENTENCE)))

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if a sentence changes from consec to concurrent`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default()))
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
          BASE_SENTENCE.copy(sentenceSequence = 3, lineSequence = 3, consecutiveToSequence = 1),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "A"),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE,
          BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2, sentenceStatus = "A"),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceCalculationType = SentenceCalculationType.ADIMP.name),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(
          BASE_SENTENCE.copy(sentenceDate = LocalDate.of(2025, 1, 2)),
        ),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = null)),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(10))),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(
        sentenceAndOffences = listOf(BASE_SENTENCE.copy(fineAmount = BigDecimal.valueOf(10))),
      ),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
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
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = null),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2001, 2, 3))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if return to custody date is removed`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2000, 1, 2))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = null),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if return to custody date is changed`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2000, 1, 2))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(returnToCustodyDate = ReturnToCustodyDate(BOOKING_ID, LocalDate.of(2001, 2, 3))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are new adjustments`() {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        adjustmentsApiData = listOf(
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
            status = AdjustmentDto.Status.ACTIVE,
          ),
        ),
      ),
    )
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are removed adjustments`() {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        adjustmentsApiData = listOf(
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
            status = AdjustmentDto.Status.ACTIVE,
          ),
        ),
      ),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()))
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasACalcToDo()
  }

  @ParameterizedTest
  @MethodSource("changedAdjustments")
  fun `should require a calc if the adjustments have changed`(previous: AdjustmentDto, current: AdjustmentDto) {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = listOf(previous)),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = listOf(current)))
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasACalcToDo()
  }

  @Test
  fun `should ignore changes to adjustments if they are not active`() {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        adjustmentsApiData = listOf(
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
            status = AdjustmentDto.Status.DELETED,
          ),
        ),
      ),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()))
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasNothingToDo()
  }

  @Test
  fun `should handle recall remand and tagged bail adjustments if they previously came from prison API and also order should not matter`() {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        prisonApiData = BookingAndSentenceAdjustments(
          bookingAdjustments = emptyList(),
          sentenceAdjustments = listOf(
            SentenceAdjustment(
              type = SentenceAdjustmentType.RECALL_SENTENCE_REMAND,
              numberOfDays = 10,
              fromDate = LocalDate.of(2015, 1, 2),
              toDate = LocalDate.of(2015, 1, 20),
              active = true,
              sentenceSequence = 1,
            ),
            SentenceAdjustment(
              type = SentenceAdjustmentType.RECALL_SENTENCE_TAGGED_BAIL,
              numberOfDays = 22,
              fromDate = LocalDate.of(2016, 1, 2),
              toDate = LocalDate.of(2016, 1, 20),
              active = true,
              sentenceSequence = 1,
            ),
          ),
        ),
      ),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        adjustmentsApiData = listOf(
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.TAGGED_BAIL,
            days = 22,
            fromDate = LocalDate.of(2016, 1, 2),
            toDate = LocalDate.of(2016, 1, 20),
            status = AdjustmentDto.Status.ACTIVE,
            sentenceSequence = 1,
          ),
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
            days = 10,
            fromDate = LocalDate.of(2015, 1, 2),
            toDate = LocalDate.of(2015, 1, 20),
            status = AdjustmentDto.Status.ACTIVE,
            sentenceSequence = 1,
          ),
        ),
      ),
    )
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasNothingToDo()
  }

  @Test
  fun `should handle the NOMIS adjustments having no to date, treat the same if the from date and number of days are the same`() {
    hasPreviousCalc()
    val previousSourceData = CalculationSourceData(
      sentenceAndOffences = listOf(
        BASE_SENTENCE,
        BASE_SENTENCE.copy(sentenceSequence = 2, lineSequence = 2),
      ),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        prisonApiData = BookingAndSentenceAdjustments(
          bookingAdjustments = listOf(
            BookingAdjustment(
              type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
              numberOfDays = 10,
              fromDate = LocalDate.of(2015, 1, 2),
              toDate = null,
              active = true,
            ),
          ),
          sentenceAdjustments = listOf(
            SentenceAdjustment(
              type = SentenceAdjustmentType.REMAND,
              numberOfDays = 20,
              fromDate = LocalDate.of(2015, 2, 3),
              toDate = null,
              active = true,
              sentenceSequence = 1,
            ),
          ),
        ),
      ),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )
    val currentSourceData = previousSourceData.copy(
      bookingAndSentenceAdjustments = AdjustmentsSourceData(
        adjustmentsApiData = listOf(
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
            days = 10,
            fromDate = LocalDate.of(2015, 1, 2),
            toDate = LocalDate.of(2015, 1, 11),
            status = AdjustmentDto.Status.ACTIVE,
          ),
          AdjustmentDto(
            person = PRISONER_DETAILS.offenderNo,
            adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
            days = 20,
            fromDate = LocalDate.of(2015, 2, 3),
            toDate = LocalDate.of(2015, 2, 22),
            status = AdjustmentDto.Status.ACTIVE,
            sentenceSequence = 1,
          ),
        ),
      ),
    )
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(previousSourceData)
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(currentSourceData)

    assertThatHasNothingToDo()
  }

  @Test
  fun `should require a calc if there are new fine payments`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = emptyList()),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are fine payments removed`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = emptyList()),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should require a calc if there are fine payments updated`() {
    hasPreviousCalc()
    whenever(sourceDataMapper.getSourceData(CALC_REQUEST)).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(10)))),
    )
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(offenderFinePayments = listOf(OffenderFinePayment(BOOKING_ID, LocalDate.of(2000, 1, 2), BigDecimal.valueOf(20)))),
    )

    assertThatHasACalcToDo()
  }

  @Test
  fun `should not require a calc if there are no sentences`() {
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(BOOKING_ID, "CONFIRMED")).thenReturn(Optional.empty())
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(
      BASE_SOURCE_DATA.copy(sentenceAndOffences = emptyList()),
    )

    assertThatHasNothingToDo()
  }

  @Test
  fun `should not require a calc if the sentences and offences are exactly the same`() {
    hasPreviousCalc()
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
    whenever(calculationSourceDataService.getCalculationSourceData(PRISONER_DETAILS, SourceDataLookupOptions.default())).thenReturn(sourceData)

    assertThatHasNothingToDo()
  }

  private fun hasPreviousCalc() {
    whenever(calculationRequestRepository.findFirstByBookingIdAndCalculationStatusOrderByCalculatedAtDesc(BOOKING_ID, "CONFIRMED")).thenReturn(Optional.of(CALC_REQUEST))
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
      courtId = null,
      courtDescription = null,
      courtTypeCode = null,
      fineAmount = null,
      revocationDates = emptyList(),
      isSDSPlus = false,
      isSDSPlusEligibleSentenceTypeLengthAndOffence = false,
      isSDSPlusOffenceInPeriod = false,
      hasAnSDSEarlyReleaseExclusion = SDSEarlyReleaseExclusionType.NO,
    )
    private val BASE_SOURCE_DATA = CalculationSourceData(
      sentenceAndOffences = listOf(BASE_SENTENCE),
      prisonerDetails = PRISONER_DETAILS,
      bookingAndSentenceAdjustments = AdjustmentsSourceData(adjustmentsApiData = emptyList()),
      offenderFinePayments = emptyList(),
      returnToCustodyDate = null,
    )

    @JvmStatic
    fun changedAdjustments(): Stream<Arguments> {
      val baseAdjustment = AdjustmentDto(
        person = PRISONER_DETAILS.offenderNo,
        adjustmentType = AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE,
        status = AdjustmentDto.Status.ACTIVE,
      )
      return Stream.of(
        Arguments.of(baseAdjustment, baseAdjustment.copy(days = 1)),
        Arguments.of(baseAdjustment, baseAdjustment.copy(fromDate = LocalDate.of(2025, 9, 6))),
        Arguments.of(baseAdjustment, baseAdjustment.copy(adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED)),
        Arguments.of(baseAdjustment, baseAdjustment.copy(sentenceSequence = 1)),
        Arguments.of(baseAdjustment.copy(days = 1), baseAdjustment),
        Arguments.of(baseAdjustment.copy(fromDate = LocalDate.of(2025, 9, 6)), baseAdjustment),
        Arguments.of(baseAdjustment.copy(adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED), baseAdjustment),
        Arguments.of(baseAdjustment.copy(sentenceSequence = 1), baseAdjustment),
        Arguments.of(baseAdjustment.copy(days = 1), baseAdjustment.copy(days = 2)),
        Arguments.of(baseAdjustment.copy(fromDate = LocalDate.of(2025, 9, 6)), baseAdjustment.copy(fromDate = LocalDate.of(2026, 9, 6))),
        Arguments.of(baseAdjustment.copy(adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED), baseAdjustment.copy(adjustmentType = AdjustmentDto.AdjustmentType.REMAND)),
        Arguments.of(baseAdjustment.copy(sentenceSequence = 1), baseAdjustment.copy(sentenceSequence = 2)),
      )
    }
  }
}

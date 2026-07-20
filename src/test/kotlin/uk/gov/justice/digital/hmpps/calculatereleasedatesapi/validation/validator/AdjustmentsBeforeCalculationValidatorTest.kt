package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.adjustmentsapi.model.AdjustmentDto
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.AdjustmentsSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.BookingAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.OffenderOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonerDetails
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceTerms
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.prisonapi.BookingAndSentenceAdjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities
import java.time.LocalDate

class AdjustmentsBeforeCalculationValidatorTest {

  private val validator = AdjustmentsBeforeCalculationValidator(ValidationUtilities(), ADJUSTMENTS_UI_URL)

  @Nested
  inner class BookingAndSentenceAdjustmentsTests {

    @Test
    fun `returns invalid date range when remand from date is after to date`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = emptyList(),
        sentenceAdjustments = listOf(
          SentenceAdjustment(
            sentenceSequence = 1,
            active = true,
            fromDate = LocalDate.of(2024, 1, 10),
            toDate = LocalDate.of(2024, 1, 1),
            numberOfDays = 9,
            type = SentenceAdjustmentType.REMAND,
          ),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.ADJUSTMENT_INVALID_DATE_RANGE,
        ),
      )
    }

    @Test
    fun `returns remand overlap message for overlapping remand adjustments`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = emptyList(),
        sentenceAdjustments = listOf(
          remand(fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 10)),
          remand(fromDate = LocalDate.of(2024, 1, 5), toDate = LocalDate.of(2024, 1, 15)),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.REMAND_OVERLAPS_WITH_REMAND,
          listOf("2024-01-01", "2024-01-10", "2024-01-05", "2024-01-15"),
        ),
      )
    }

    @Test
    fun `returns inactive sentence message when adjustment sentence sequence is not in current source data`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = emptyList(),
        sentenceAdjustments = listOf(
          remand(sentenceSequence = 99, fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 2)),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.ADJUSTMENT_LINKED_TO_INACTIVE_SENTENCE,
          listOf(ADJUSTMENTS_UI_URL, OFFENDER_NO),
        ),
      )
    }

    @Test
    fun `returns future-dated UAL message using to date`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = listOf(
          BookingAdjustment(
            active = true,
            fromDate = LocalDate.now().minusDays(7),
            toDate = LocalDate.now().plusDays(2),
            numberOfDays = 9,
            type = BookingAdjustmentType.UNLAWFULLY_AT_LARGE,
          ),
        ),
        sentenceAdjustments = emptyList(),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL),
      )
    }

    @Test
    fun `returns future-dated ADA message`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = listOf(
          BookingAdjustment(
            active = true,
            fromDate = LocalDate.now().plusDays(1),
            numberOfDays = 1,
            type = BookingAdjustmentType.ADDITIONAL_DAYS_AWARDED,
          ),
        ),
        sentenceAdjustments = emptyList(),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA),
      )
    }

    @Test
    fun `returns future-dated RADA message`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = listOf(
          BookingAdjustment(
            active = true,
            fromDate = LocalDate.now().plusDays(1),
            numberOfDays = 1,
            type = BookingAdjustmentType.RESTORED_ADDITIONAL_DAYS_AWARDED,
          ),
        ),
        sentenceAdjustments = emptyList(),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA),
      )
    }

    @Test
    fun `returns REMAND_FROM_TO_DATES_REQUIRED when remand has no dates`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = emptyList(),
        sentenceAdjustments = listOf(
          SentenceAdjustment(
            sentenceSequence = 1,
            active = true,
            fromDate = null,
            toDate = null,
            numberOfDays = 0,
            type = SentenceAdjustmentType.REMAND,
          ),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).contains(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    }

    @Test
    fun `returns no messages for valid adjustments`() {
      val adjustments = BookingAndSentenceAdjustments(
        bookingAdjustments = emptyList(),
        sentenceAdjustments = listOf(
          remand(fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 10)),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).isEmpty()
    }
  }

  @Nested
  inner class AdjustmentsDtoTests {

    @Test
    fun `returns invalid date range when remand from date is after to date`() {
      val adjustments = listOf(
        remandDto(
          sentenceSequence = 1,
          fromDate = LocalDate.of(2024, 1, 10),
          toDate = LocalDate.of(2024, 1, 1),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.ADJUSTMENT_INVALID_DATE_RANGE,
        ),
      )
    }

    @Test
    fun `returns REMAND_FROM_TO_DATES_REQUIRED when remand has no from date`() {
      val adjustments = listOf(
        AdjustmentDto(
          person = OFFENDER_NO,
          adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
          sentenceSequence = 1,
          fromDate = null,
          toDate = LocalDate.of(2024, 1, 10),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).contains(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    }

    @Test
    fun `returns REMAND_FROM_TO_DATES_REQUIRED when remand has no to date`() {
      val adjustments = listOf(
        AdjustmentDto(
          person = OFFENDER_NO,
          adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
          sentenceSequence = 1,
          fromDate = LocalDate.of(2024, 1, 1),
          toDate = null,
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).contains(ValidationMessage(ValidationCode.REMAND_FROM_TO_DATES_REQUIRED))
    }

    @Test
    fun `returns remand overlap message for overlapping remand adjustments`() {
      val adjustments = listOf(
        remandDto(sentenceSequence = 1, fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 10)),
        remandDto(sentenceSequence = 1, fromDate = LocalDate.of(2024, 1, 5), toDate = LocalDate.of(2024, 1, 15)),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.REMAND_OVERLAPS_WITH_REMAND,
          listOf("2024-01-01", "2024-01-10", "2024-01-05", "2024-01-15"),
        ),
      )
    }

    @Test
    fun `returns inactive sentence message when adjustment sentence sequence is not in current source data`() {
      val adjustments = listOf(
        remandDto(sentenceSequence = 99, fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 2)),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(
          ValidationCode.ADJUSTMENT_LINKED_TO_INACTIVE_SENTENCE,
          listOf(ADJUSTMENTS_UI_URL, OFFENDER_NO),
        ),
      )
    }

    @Test
    fun `returns future-dated UAL message using to date`() {
      val adjustments = listOf(
        AdjustmentDto(
          person = OFFENDER_NO,
          adjustmentType = AdjustmentDto.AdjustmentType.UNLAWFULLY_AT_LARGE,
          fromDate = LocalDate.now().minusDays(3),
          toDate = LocalDate.now().plusDays(1),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_UAL),
      )
    }

    @Test
    fun `returns future-dated ADA message`() {
      val adjustments = listOf(
        AdjustmentDto(
          person = OFFENDER_NO,
          adjustmentType = AdjustmentDto.AdjustmentType.ADDITIONAL_DAYS_AWARDED,
          fromDate = LocalDate.now().plusDays(1),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_ADA),
      )
    }

    @Test
    fun `returns future-dated RADA message`() {
      val adjustments = listOf(
        AdjustmentDto(
          person = OFFENDER_NO,
          adjustmentType = AdjustmentDto.AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED,
          fromDate = LocalDate.now().plusDays(1),
        ),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).containsExactly(
        ValidationMessage(ValidationCode.ADJUSTMENT_FUTURE_DATED_RADA),
      )
    }

    @Test
    fun `returns no messages for valid adjustments`() {
      val adjustments = listOf(
        remandDto(sentenceSequence = 1, fromDate = LocalDate.of(2024, 1, 1), toDate = LocalDate.of(2024, 1, 10)),
      )

      val result = validator.validateAdjustmentsBeforeCalculation(adjustments, sourceData())

      assertThat(result).isEmpty()
    }
  }

  private fun sourceData(): CalculationSourceData = CalculationSourceData(
    sentenceAndOffences = listOf(sentenceAndOffence()),
    prisonerDetails = PrisonerDetails(bookingId = 1L, offenderNo = OFFENDER_NO, dateOfBirth = LocalDate.of(1980, 1, 1)),
    bookingAndSentenceAdjustments = AdjustmentsSourceData(prisonApiData = BookingAndSentenceAdjustments(emptyList(), emptyList())),
    returnToCustodyDate = null,
  )

  private fun sentenceAndOffence() = SentenceAndOffenceWithReleaseArrangements(
    bookingId = 1L,
    sentenceSequence = 1,
    lineSequence = 1,
    caseSequence = 1,
    consecutiveToSequence = null,
    sentenceStatus = "A",
    sentenceCategory = "2003",
    sentenceCalculationType = "ADIMP",
    sentenceTypeDescription = "ADIMP",
    sentenceDate = LocalDate.of(2024, 1, 1),
    terms = listOf(SentenceTerms(years = 1, code = SentenceTerms.IMPRISONMENT_TERM_CODE)),
    offence = OffenderOffence(
      offenderChargeId = 1L,
      offenceStartDate = LocalDate.of(2023, 1, 1),
      offenceCode = "CODE",
      offenceDescription = "Description",
    ),
    caseReference = null,
    courtId = null,
    courtDescription = null,
    courtTypeCode = null,
    fineAmount = null,
    revocationDates = emptyList(),
  )

  private fun remand(sentenceSequence: Int = 1, fromDate: LocalDate, toDate: LocalDate): SentenceAdjustment = SentenceAdjustment(
    sentenceSequence = sentenceSequence,
    active = true,
    fromDate = fromDate,
    toDate = toDate,
    numberOfDays = 1,
    type = SentenceAdjustmentType.REMAND,
  )

  private fun remandDto(sentenceSequence: Int = 1, fromDate: LocalDate, toDate: LocalDate): AdjustmentDto = AdjustmentDto(
    person = OFFENDER_NO,
    adjustmentType = AdjustmentDto.AdjustmentType.REMAND,
    sentenceSequence = sentenceSequence,
    fromDate = fromDate,
    toDate = toDate,
  )

  companion object {
    private const val OFFENDER_NO = "A1234BC"
    private const val ADJUSTMENTS_UI_URL = "https://adjustments-ui.example.com"
  }
}

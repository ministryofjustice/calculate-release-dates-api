package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustments
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Offender
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import java.time.LocalDate

class UalValidatorTest {

  private val validator = UalValidator()

  @Test
  fun `should return no messages if no UAL`() {
    val messages = validator.validate(MINIMAL_BOOKING)
    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return no messages if only a single UAL`() {
    val adjustments = Adjustments()
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 1),
        fromDate = LocalDate.of(2020, 1, 1),
        toDate = LocalDate.of(2020, 1, 10),
        numberOfDays = 10,
      ),
    )
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return no messages if UAL does not overlap`() {
    val adjustments = Adjustments()
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 1),
        fromDate = LocalDate.of(2020, 1, 1),
        toDate = LocalDate.of(2020, 1, 10),
        numberOfDays = 10,
      ),
    )
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 11),
        fromDate = LocalDate.of(2020, 1, 11),
        toDate = LocalDate.of(2020, 1, 20),
        numberOfDays = 10,
      ),
    )
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).isEmpty()
  }

  @Test
  fun `should return validation error if UAL overlaps partially`() {
    val adjustments = Adjustments()
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 5),
        fromDate = LocalDate.of(2020, 1, 5),
        toDate = LocalDate.of(2020, 1, 15),
        numberOfDays = 10,
      ),
    )
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 10),
        fromDate = LocalDate.of(2020, 1, 10),
        toDate = LocalDate.of(2020, 1, 20),
        numberOfDays = 10,
      ),
    )
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).containsOnly(ValidationMessage(ValidationCode.DUPLICATE_OR_OVERLAPPING_UAL))
  }

  @Test
  fun `should return validation error if UAL shares an end date and start date`() {
    val adjustments = Adjustments()
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 1),
        fromDate = LocalDate.of(2020, 1, 1),
        toDate = LocalDate.of(2020, 1, 10),
        numberOfDays = 10,
      ),
    )
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 10),
        fromDate = LocalDate.of(2020, 1, 10),
        toDate = LocalDate.of(2020, 1, 20),
        numberOfDays = 10,
      ),
    )
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).containsOnly(ValidationMessage(ValidationCode.DUPLICATE_OR_OVERLAPPING_UAL))
  }

  @Test
  fun `should return validation error if UAL overlaps completely`() {
    val adjustments = Adjustments()
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 5),
        fromDate = LocalDate.of(2020, 1, 5),
        toDate = LocalDate.of(2020, 1, 8),
        numberOfDays = 10,
      ),
    )
    adjustments.addAdjustment(
      AdjustmentType.UNLAWFULLY_AT_LARGE,
      Adjustment(
        appliesToSentencesFrom = LocalDate.of(2020, 1, 1),
        fromDate = LocalDate.of(2020, 1, 1),
        toDate = LocalDate.of(2020, 1, 10),
        numberOfDays = 10,
      ),
    )
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).containsOnly(ValidationMessage(ValidationCode.DUPLICATE_OR_OVERLAPPING_UAL))
  }

  @Test
  fun `should return validation error if UAL is duplicated`() {
    val adjustments = Adjustments()
    val ual = Adjustment(
      appliesToSentencesFrom = LocalDate.of(2020, 1, 5),
      fromDate = LocalDate.of(2020, 1, 5),
      toDate = LocalDate.of(2020, 1, 8),
      numberOfDays = 10,
    )
    adjustments.addAdjustment(AdjustmentType.UNLAWFULLY_AT_LARGE, ual)
    adjustments.addAdjustment(AdjustmentType.UNLAWFULLY_AT_LARGE, ual)
    val messages = validator.validate(MINIMAL_BOOKING.copy(adjustments = adjustments))
    assertThat(messages).containsOnly(ValidationMessage(ValidationCode.DUPLICATE_OR_OVERLAPPING_UAL))
  }

  companion object {
    private val MINIMAL_BOOKING = Booking(
      offender = Offender(
        reference = "A1234BC",
        dateOfBirth = LocalDate.of(1982, 6, 15),
      ),
      sentences = emptyList(),
    )
  }
}

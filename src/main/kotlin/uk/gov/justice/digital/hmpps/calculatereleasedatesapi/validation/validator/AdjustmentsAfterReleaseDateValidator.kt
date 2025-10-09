package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Adjustment
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Term
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class AdjustmentsAfterReleaseDateValidator : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    val adjustments = getSortedAdjustments(booking)
    val nonTermSentences = calculationOutput.sentenceGroup.flatMap { it.sentences }.filterNot { it is Term }

    if (nonTermSentences.isEmpty()) {
      return emptyList()
    }

    val latestReleaseDate = nonTermSentences.maxOf { it.sentenceCalculation.releaseDateDefaultedByCommencement() }
    val messages = mutableSetOf<ValidationMessage>()

    adjustments.forEach { (type, adjustment) ->
      if (adjustment.appliesToSentencesFrom.isAfter(latestReleaseDate)) {
        if (type == AdjustmentType.ADDITIONAL_DAYS_AWARDED) {
          messages.add(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_ADA))
        } else {
          messages.add(ValidationMessage(ValidationCode.ADJUSTMENT_AFTER_RELEASE_RADA))
        }
      }
    }
    return messages.toList()
  }
  private fun getSortedAdjustments(booking: Booking): List<Pair<AdjustmentType, Adjustment>> {
    val adas = booking.adjustments.getOrEmptyList(AdjustmentType.ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.ADDITIONAL_DAYS_AWARDED to it }

    val radas = booking.adjustments.getOrEmptyList(AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED)
      .map { AdjustmentType.RESTORATION_OF_ADDITIONAL_DAYS_AWARDED to it }

    return (adas + radas).sortedBy { it.second.appliesToSentencesFrom }
  }
  override fun validationOrder() = ValidationOrder.INVALID
}

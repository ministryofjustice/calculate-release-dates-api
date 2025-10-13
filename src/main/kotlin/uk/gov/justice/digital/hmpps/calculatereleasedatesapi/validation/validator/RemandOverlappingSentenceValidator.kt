package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import org.threeten.extra.LocalDateRange
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.AdjustmentType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationUtilities

@Component
class RemandOverlappingSentenceValidator(private val validationUtilities: ValidationUtilities) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    val remandPeriods = booking.adjustments.getOrEmptyList(AdjustmentType.REMAND)

    val validationMessages = mutableSetOf<ValidationMessage>()
    if (remandPeriods.isNotEmpty()) {
      val remandRanges = remandPeriods.map { LocalDateRange.of(it.fromDate, it.toDate) }

      val sentenceRanges = calculationOutput.sentenceGroup.filter { period -> period.sentences.none { it.isRecall() } }
        .filter { it.from != it.to }.map { LocalDateRange.of(it.from, it.to) }

      remandRanges.forEach { remandRange ->
        sentenceRanges.forEach { sentenceRange ->
          if (remandRange.isConnected(sentenceRange)) {
            validationMessages.add(
              ValidationMessage(
                ValidationCode.REMAND_OVERLAPS_WITH_SENTENCE,
                arguments = validationUtilities.buildOverlappingMessageArguments(sentenceRange, remandRange),
              ),
            )
          }
        }
      }
    }

    return validationMessages.toList()
  }

  override fun validationOrder() = ValidationOrder.INVALID
}

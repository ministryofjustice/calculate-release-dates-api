package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.ExtinguishedSentenceGroupChecker.ExtinguishedSentenceValidationMode.ROUTE_TO_MANUAL

@Component
class SendPreProgressionExtinguishedSentenceToManualValidator(
  private val extinguishedSentenceGroupChecker: ExtinguishedSentenceGroupChecker,
) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    if (extinguishedSentenceGroupChecker.mode(calculationOutput, booking) != ROUTE_TO_MANUAL) {
      return emptyList()
    }
    return calculationOutput.sentenceGroup.flatMap { validateSentenceHasNotBeenExtinguished(it) }
  }

  private fun validateSentenceHasNotBeenExtinguished(sentenceGroup: SentenceGroup): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val result = extinguishedSentenceGroupChecker.check(sentenceGroup)
    if (result.isExtinguished) {
      messages += ValidationMessage(ValidationCode.PROGRESSION_MODEL_UNSUPPORTED_EXTINGUISHED_SENTENCE)
    }
    return messages
  }

  override fun validationOrder() = ValidationOrder.UNSUPPORTED
}

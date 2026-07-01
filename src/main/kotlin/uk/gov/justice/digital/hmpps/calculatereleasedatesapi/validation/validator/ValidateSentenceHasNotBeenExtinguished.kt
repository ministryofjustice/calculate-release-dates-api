package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator.ExtinguishedSentenceGroupChecker.ExtinguishedSentenceValidationMode.INVALID

@Component
class ValidateSentenceHasNotBeenExtinguished(
  private val extinguishedSentenceGroupChecker: ExtinguishedSentenceGroupChecker,
  @Value($$"${adjustments.ui.url}") private val adjustmentsUiUrl: String,
) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> {
    if (extinguishedSentenceGroupChecker.mode(calculationOutput, booking) != INVALID) {
      return emptyList()
    }
    return calculationOutput.sentenceGroup.flatMap { validateSentenceHasNotBeenExtinguished(it, booking) }
  }

  private fun validateSentenceHasNotBeenExtinguished(sentenceGroup: SentenceGroup, booking: Booking): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val result = extinguishedSentenceGroupChecker.check(sentenceGroup)
    if (result.isExtinguished) {
      val adjustmentsImpactingReleaseDate: String = when {
        result.hasRemand && result.hasTaggedBail -> "remand and tagged bail"
        result.hasRemand -> "remand"
        result.hasTaggedBail -> "tagged bail"
        else -> throw IllegalStateException("Must have had remand or tagged bail to be extinguished")
      }
      messages += ValidationMessage(
        ValidationCode.RELEASE_DATE_BEFORE_SENTENCE_DATE,
        listOf(
          adjustmentsImpactingReleaseDate,
          adjustmentsUiUrl,
          booking.offender.reference,
        ),
      )
    }
    return messages
  }

  override fun validationOrder() = ValidationOrder.INVALID
}

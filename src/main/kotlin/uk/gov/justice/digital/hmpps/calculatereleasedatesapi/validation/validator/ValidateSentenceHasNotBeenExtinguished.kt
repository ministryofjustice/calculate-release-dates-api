package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.Booking
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.CalculationOutput
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceCalculation
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceGroup
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.service.sentence.SentencesExtractionService
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class ValidateSentenceHasNotBeenExtinguished(private val extractionService: SentencesExtractionService) : PostCalculationValidator {

  override fun validate(calculationOutput: CalculationOutput, booking: Booking): List<ValidationMessage> = calculationOutput.sentenceGroup.map { validateSentenceHasNotBeenExtinguished(it) }.flatten()

  private fun validateSentenceHasNotBeenExtinguished(sentenceGroup: SentenceGroup): List<ValidationMessage> {
    val messages = mutableListOf<ValidationMessage>()
    val determinateSentences = sentenceGroup.sentences.filter { !it.isRecall() }
    if (determinateSentences.isNotEmpty()) {
      val earliestSentenceDate = determinateSentences.minOf { it.sentencedAt }
      val latestReleaseDateSentence = extractionService.mostRecentSentence(
        determinateSentences,
        SentenceCalculation::adjustedUncappedDeterminateReleaseDate,
      )
      if (earliestSentenceDate.minusDays(1)
          .isAfter(latestReleaseDateSentence.sentenceCalculation.adjustedUncappedDeterminateReleaseDate)
      ) {
        val hasRemand = latestReleaseDateSentence.sentenceCalculation.adjustments.remand != 0L
        val hasTaggedBail =
          latestReleaseDateSentence.sentenceCalculation.adjustments.taggedBail != 0L
        if (hasRemand) messages += ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_REMAND)

        if (hasTaggedBail) messages += ValidationMessage(ValidationCode.CUSTODIAL_PERIOD_EXTINGUISHED_TAGGED_BAIL)
      }
    }
    return messages
  }

  override fun validationOrder() = ValidationOrder.INVALID
}

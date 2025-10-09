package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.validator

import org.springframework.stereotype.Component
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationMessage
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationOrder

@Component
class ConsecutiveSentenceValidator : PreCalculationSourceDataValidator {

  override fun validate(sourceData: CalculationSourceData): List<ValidationMessage> = validateNoBrokenConsecutiveChains(sourceData.sentenceAndOffences) + validateConsecutiveToSentenceInPast(sourceData.sentenceAndOffences)

  fun validateNoBrokenConsecutiveChains(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val sentenceSequences = mutableSetOf<Int>()
    val consecutiveToSequences = mutableSetOf<Int>()

    for (sentence in sentences) {
      sentenceSequences += sentence.sentenceSequence
      sentence.consecutiveToSequence?.let { consecutiveToSequences += it }
    }

    if (consecutiveToSequences.isEmpty()) return emptyList()

    val hasBrokenChain = consecutiveToSequences.any { it !in sentenceSequences }

    return if (hasBrokenChain) {
      listOf(ValidationMessage(ValidationCode.BROKEN_CONSECUTIVE_CHAINS))
    } else {
      emptyList()
    }
  }

  private fun validateConsecutiveToSentenceInPast(sentences: List<SentenceAndOffence>): List<ValidationMessage> {
    val consecutiveToSentences = sentences.filter { it.consecutiveToSequence != null }
    val sentencesMap = sentences.associateBy { it.sentenceSequence }

    return consecutiveToSentences.mapNotNull {
      val sentence = sentencesMap[it.consecutiveToSequence]
      if (sentence != null && sentence.sentenceDate.isAfter(it.sentenceDate)) {
        return@mapNotNull ValidationMessage(
          ValidationCode.CONSECUTIVE_TO_SENTENCE_IMPOSED_AFTER,
          listOf(it.caseSequence.toString(), it.lineSequence.toString()),
        )
      } else {
        return@mapNotNull null
      }
    }
  }

  override fun validationOrder() = ValidationOrder.INVALID
}

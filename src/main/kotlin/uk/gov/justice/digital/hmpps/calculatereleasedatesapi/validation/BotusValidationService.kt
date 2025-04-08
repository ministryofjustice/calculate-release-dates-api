package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.CalculationSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE

@Service
class BotusValidationService(
  private val featureToggles: FeatureToggles,
) {

  internal fun validate(sourceData: CalculationSourceData): List<ValidationMessage> {
    return validateUnsupportedConsecutiveBotusSentences(sourceData)
  }

  private fun validateUnsupportedConsecutiveBotusSentences(sourceData: CalculationSourceData): List<ValidationMessage> {
    val consecutiveSentences = sourceData.sentenceAndOffences
      .filter { it.consecutiveToSequence != null }

    if (consecutiveSentences.any { isBotusSentence(it) }) {
      return listOf(ValidationMessage(code = BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE))
    }

    getConsecutiveChains(consecutiveSentences).forEach { chain ->
      if (isBotusAdjacentSentence(sourceData, chain.first())) {
        return listOf(ValidationMessage(code = BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE))
      }
    }

    return emptyList()
  }

  private fun isBotusSentence(sentence: SentenceAndOffenceWithReleaseArrangements): Boolean {
    return SentenceCalculationType.from(sentence.sentenceCalculationType) == BOTUS
  }

  private fun isBotusAdjacentSentence(sourceData: CalculationSourceData, index: Int): Boolean {
    val sentence = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == index }
    return sentence?.let { isBotusSentence(it) } == true
  }

  private fun getConsecutiveChains(
    consecutiveSentences: List<SentenceAndOffenceWithReleaseArrangements>,
  ): List<List<Int>> {
    val sentenceChains = mutableListOf<MutableList<Int>>()

    for (sentence in consecutiveSentences.sortedBy { it.consecutiveToSequence }) {
      val sequence = sentence.consecutiveToSequence ?: continue
      if (sentenceChains.isEmpty() || sentenceChains.last().last() != sequence - 1) {
        sentenceChains.add(mutableListOf(sequence))
      } else {
        sentenceChains.last().add(sequence)
      }
    }

    return sentenceChains
  }
}

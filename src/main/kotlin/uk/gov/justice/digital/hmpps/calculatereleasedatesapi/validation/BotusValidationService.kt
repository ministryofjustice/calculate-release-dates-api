package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation

import org.springframework.stereotype.Service
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.config.FeatureToggles
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.SentenceAndOffenceWithReleaseArrangements
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.PrisonApiSourceData
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceAndOffence
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model.external.SentenceCalculationType.BOTUS
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE
import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.validation.ValidationCode.BOTUS_CONSECUTIVE_TO_OTHER_SENTENCE

@Service
class BotusValidationService(
  private val featureToggles: FeatureToggles,
) {

  internal fun validate(sourceData: PrisonApiSourceData): List<ValidationMessage> {
    if (featureToggles.botusConcurrentJourney) {
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
    }

    if (!featureToggles.botusConsecutiveJourney) {
      val botusSentences = getBotusSentences(sourceData)

      if (botusSentences.isEmpty() || botusSentences.size == sourceData.sentenceAndOffences.size) {
        return emptyList()
      }

      return listOf(ValidationMessage(code = BOTUS_CONSECUTIVE_OR_CONCURRENT_TO_OTHER_SENTENCE))
    }

    return emptyList()
  }

  private fun getBotusSentences(sourceData: PrisonApiSourceData): List<SentenceAndOffence> {
    return sourceData.sentenceAndOffences.filter {
      SentenceCalculationType.from(it.sentenceCalculationType) == BOTUS
    }
  }

  private fun isBotusSentence(sentence: SentenceAndOffenceWithReleaseArrangements): Boolean {
    return SentenceCalculationType.from(sentence.sentenceCalculationType) == BOTUS
  }

  private fun isBotusAdjacentSentence(sourceData: PrisonApiSourceData, index: Int): Boolean {
    val sentence = sourceData.sentenceAndOffences.firstOrNull { it.sentenceSequence == index }
    return sentence?.let { isBotusSentence(it) } == true
  }

  private fun getConsecutiveChains(
    consecutiveSentences: List<SentenceAndOffenceWithReleaseArrangements>,
  ): List<List<Int>> {
    val sentenceChains = mutableListOf<MutableList<Int>>()

    for (sentence in consecutiveSentences) {
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

package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack
import java.time.temporal.ChronoUnit

class StandardConsecutiveSentence(orderedStandardSentences: List<StandardSentence>) : AbstractConsecutiveSentence<StandardSentence>(
  orderedStandardSentences
) {

  override fun buildString(): String {
    return "ConsecutiveSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedStandardSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    var date = sentencedAt
    orderedStandardSentences.forEach {
      date = date.plusDays(it.duration.getLengthInDays(date).toLong())
    }
    return (ChronoUnit.DAYS.between(sentencedAt, date)).toInt()
  }

  private fun hasAfterCjaLaspo(): Boolean {
    return orderedStandardSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
  }

  private fun hasBeforeCjaLaspo(): Boolean {
    return orderedStandardSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
  }

  fun hasOraSentences(): Boolean {
    return orderedStandardSentences.any(StandardSentence::isOraSentence)
  }

  fun hasNonOraSentences(): Boolean {
    return orderedStandardSentences.any { !it.isOraSentence() }
  }

  fun isMadeUpOfBeforeAndAfterCjaLaspoSentences(): Boolean {
    return hasBeforeCjaLaspo() && hasAfterCjaLaspo()
  }

  fun isMadeUpOfOnlyBeforeCjaLaspoSentences(): Boolean {
    return hasBeforeCjaLaspo() && !hasAfterCjaLaspo()
  }

  fun isMadeUpOfOnlyAfterCjaLaspoSentences(): Boolean {
    return hasAfterCjaLaspo() && !hasBeforeCjaLaspo()
  }

  private fun hasSdsPlusSentences(): Boolean {
    return orderedStandardSentences.any(StandardSentence::isSdsSentence)
  }

  private fun hasSdsSentences(): Boolean {
    return orderedStandardSentences.any { !it.isSdsSentence() }
  }

  fun isMadeUpOfSdsPlusAndSdsSentences(): Boolean {
    return hasSdsSentences() && hasSdsPlusSentences()
  }

  fun isMadeUpOfOnlySdsPlusSentences(): Boolean {
    return !hasSdsSentences() && hasSdsPlusSentences()
  }
}

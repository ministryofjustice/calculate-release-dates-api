package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import uk.gov.justice.digital.hmpps.calculatereleasedatesapi.enumerations.SentenceIdentificationTrack

class StandardDeterminateConsecutiveSentence(orderedStandardSentences: List<StandardDeterminateSentence>) :
  AbstractConsecutiveSentence<StandardDeterminateSentence>(
    orderedStandardSentences
  ),
  StandardDeterminate {

  override fun buildString(): String {
    return "StandardDeterminateConsecutiveSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    val duration = this.orderedSentences.map { it.duration }
      .reduce { acc, duration -> acc.appendAll(duration.durationElements) }
    return duration.getLengthInDays(sentencedAt)
  }

  private fun hasAfterCjaLaspo(): Boolean {
    return orderedSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_AFTER_CJA_LASPO }
  }

  private fun hasBeforeCjaLaspo(): Boolean {
    return orderedSentences.any() { it.identificationTrack === SentenceIdentificationTrack.SDS_BEFORE_CJA_LASPO }
  }

  fun hasOraSentences(): Boolean {
    return orderedSentences.any(StandardDeterminateSentence::isOraSentence)
  }

  fun hasNonOraSentences(): Boolean {
    return orderedSentences.any { !it.isOraSentence() }
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

  private fun hasSdsTwoThirdsReleaseSentence(): Boolean {
    return orderedSentences.any(StandardDeterminateSentence::isTwoThirdsReleaseSentence)
  }

  private fun hasSdsHalfwayReleaseSentence(): Boolean {
    return orderedSentences.any { !it.isTwoThirdsReleaseSentence() }
  }

  fun isMadeUpOfSdsHalfwayReleaseAndTwoThirdsReleaseSentence(): Boolean {
    return hasSdsHalfwayReleaseSentence() && hasSdsTwoThirdsReleaseSentence()
  }

  fun isMadeUpOfOnlySdsTwoThirdsReleaseSentences(): Boolean {
    return !hasSdsHalfwayReleaseSentence() && hasSdsTwoThirdsReleaseSentence()
  }
}

package uk.gov.justice.digital.hmpps.calculatereleasedatesapi.model

import java.time.LocalDate

/**
 * This class is used to model consecutive Extended Determinate Sentences.
 */
class ExtendedDeterminateConsecutiveSentence(
  orderedStandardSentences: List<ExtendedDeterminateSentence>
) : AbstractConsecutiveSentence<ExtendedDeterminateSentence>(
  orderedStandardSentences
),
  ExtendedDeterminate {

  override fun buildString(): String {
    return "ExtendedDeterminateSentence\t:\t\n" +
      "Number of sentences\t:\t${orderedSentences.size}\n" +
      "Sentence Types\t:\t$releaseDateTypes\n" +
      "Number of Days in Sentence\t:\t${getLengthInDays()}\n" +
      sentenceCalculation.buildString(releaseDateTypes)
  }

  override fun getLengthInDays(): Int {
    return orderedSentences
      .map { it.custodialDuration.appendAll(it.extensionDuration.durationElements) }
      .reduce { acc, it -> acc.appendAll(it.durationElements) }
      .getLengthInDays(sentencedAt)
  }

  fun hasAutomaticRelease(): Boolean {
    return orderedSentences.any { it.automaticRelease }
  }

  fun hasDiscretionaryRelease(): Boolean {
    return orderedSentences.any { !it.automaticRelease }
  }

  fun hasAutomaticAndDiscretionaryRelease(): Boolean {
    return hasAutomaticRelease() && hasDiscretionaryRelease()
  }

  fun getAutomaticReleaseCustodialLengthInDays(): Int {
    return orderedSentences
      .filter { it.automaticRelease }
      .map { it.custodialDuration }
      .reduce { acc, it -> acc.appendAll(it.durationElements) }
      .getLengthInDays(sentencedAt)
  }

  fun getDiscretionaryReleaseCustodialLengthInDays(startDate: LocalDate): Int {
    return orderedSentences
      .filter { !it.automaticRelease }
      .map { it.custodialDuration }
      .reduce { acc, it -> acc.appendAll(it.durationElements) }
      .getLengthInDays(startDate)
  }
}
